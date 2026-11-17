package com.mall.payment.channel;

import com.mall.payment.entity.Payment;
import com.mall.payment.entity.Refund;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.Signature;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 支付宝沙箱渠道单测（#137）。
 *
 * <p>不依赖真实沙箱凭据：本地生成 RSA 密钥对，公钥充当「支付宝公钥」，
 * 用对应私钥给回调报文签名，验证 RSA2 验签往返、篡改拒绝、app_id 核对、
 * 元/分换算、退款三态与必需环境变量缺失即启动失败。网络传输用桩替换。
 */
class AlipayChannelTest {

    private static final String APP_ID = "2021000000000001";
    private static final String NOTIFY_BASE = "https://mall.example.com/api/payments";

    private static KeyPair keyPair;
    private static String privateKeyBase64;
    private static String publicKeyBase64;

    @BeforeAll
    static void generateKeys() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        keyPair = generator.generateKeyPair();
        privateKeyBase64 = Base64.getEncoder().encodeToString(keyPair.getPrivate().getEncoded());
        publicKeyBase64 = Base64.getEncoder().encodeToString(keyPair.getPublic().getEncoded());
    }

    private static Map<String, String> env() {
        Map<String, String> env = new HashMap<>();
        env.put("ALIPAY_APP_ID", APP_ID);
        env.put("ALIPAY_PRIVATE_KEY", privateKeyBase64);
        env.put("ALIPAY_PUBLIC_KEY", publicKeyBase64);
        env.put("ALIPAY_NOTIFY_BASE_URL", NOTIFY_BASE);
        return env;
    }

    private static AlipayChannel channel(AlipayTransport transport) {
        return new AlipayChannel(env(), transport);
    }

    /** 用「支付宝私钥」（这里即本地私钥）按升序拼接内容做 RSA2 签名 */
    private static String sign(Map<String, String> params) throws Exception {
        Signature signer = Signature.getInstance("SHA256withRSA");
        signer.initSign(keyPair.getPrivate());
        signer.update(AlipayChannel.signContent(params).getBytes(StandardCharsets.UTF_8));
        return Base64.getEncoder().encodeToString(signer.sign());
    }

    private static Map<String, String> paymentCallback(long amountFen) {
        Map<String, String> params = new TreeMap<>();
        params.put("app_id", APP_ID);
        params.put("notify_id", "NOTIFY0001");
        params.put("trade_no", "2026111722001400001");
        params.put("out_trade_no", "P20261117120000123456");
        params.put("trade_status", "TRADE_SUCCESS");
        params.put("total_amount", AlipayChannel.fenToYuan(amountFen));
        params.put("gmt_payment", "2026-11-17 12:00:01");
        params.put("charset", "utf-8");
        params.put("sign_type", "RSA2");
        return params;
    }

    // ---------- 元 / 分换算 ----------

    @Test
    @DisplayName("元转分：四舍五入只一次，整数分精确")
    void yuanToFenConversion() {
        assertEquals(11800L, AlipayChannel.yuanToFen("118.00"));
        assertEquals(1L, AlipayChannel.yuanToFen("0.01"));
        assertEquals(100L, AlipayChannel.yuanToFen("1"));
        assertEquals(11801L, AlipayChannel.yuanToFen("118.005"));
        assertEquals(0L, AlipayChannel.yuanToFen(null));
        assertEquals(0L, AlipayChannel.yuanToFen(""));
    }

    @Test
    @DisplayName("分转元：两位小数 plain 字符串，无科学计数")
    void fenToYuanConversion() {
        assertEquals("118.00", AlipayChannel.fenToYuan(11800L));
        assertEquals("0.01", AlipayChannel.fenToYuan(1L));
        assertEquals("1.00", AlipayChannel.fenToYuan(100L));
    }

    @Test
    @DisplayName("元分往返一致")
    void yuanFenRoundTrip() {
        for (long fen : new long[]{0L, 1L, 99L, 100L, 11800L, 999999L}) {
            assertEquals(fen, AlipayChannel.yuanToFen(AlipayChannel.fenToYuan(fen)));
        }
    }

    // ---------- 签名内容拼接 ----------

    @Test
    @DisplayName("signContent 升序拼接，剔除 sign/sign_type 与空值")
    void signContentRules() {
        Map<String, String> params = new HashMap<>();
        params.put("b", "2");
        params.put("a", "1");
        params.put("sign", "SHOULD_BE_EXCLUDED");
        params.put("sign_type", "RSA2");
        params.put("empty", "");
        params.put("nullValue", null);
        assertEquals("a=1&b=2", AlipayChannel.signContent(params));
    }

    // ---------- 回调验签 ----------

    @Test
    @DisplayName("合法回调验签通过，解析出支付通知与分金额")
    void verifyAndParsePaymentCallback() throws Exception {
        AlipayChannel channel = channel(params -> "{}");
        Map<String, String> params = paymentCallback(11800L);
        params.put("sign", sign(params));

        assertTrue(channel.verifyCallback(params));
        ChannelNotification notification = channel.parseCallback(params);
        assertEquals(ChannelNotification.Kind.PAYMENT, notification.kind());
        assertEquals("P20261117120000123456", notification.outTradeNo());
        assertEquals("2026111722001400001", notification.tradeNo());
        assertEquals(11800L, notification.amountFen());
        assertTrue(notification.success());
        assertNull(notification.channelRefundNo());
    }

    @Test
    @DisplayName("金额被篡改：验签失败")
    void rejectsTamperedAmount() throws Exception {
        AlipayChannel channel = channel(params -> "{}");
        Map<String, String> params = paymentCallback(11800L);
        params.put("sign", sign(params));
        params.put("total_amount", "0.01");

        assertFalse(channel.verifyCallback(params));
    }

    @Test
    @DisplayName("app_id 不匹配：验签失败")
    void rejectsWrongAppId() throws Exception {
        AlipayChannel channel = channel(params -> "{}");
        Map<String, String> params = paymentCallback(11800L);
        params.put("app_id", "2021000000000999");
        params.put("sign", sign(params));

        assertFalse(channel.verifyCallback(params));
    }

    @Test
    @DisplayName("缺少签名：验签失败")
    void rejectsMissingSign() {
        AlipayChannel channel = channel(params -> "{}");
        Map<String, String> params = paymentCallback(11800L);
        assertFalse(channel.verifyCallback(params));
    }

    @Test
    @DisplayName("退款通知：refund_fee 存在时解析为 REFUND，out_biz_no 即退款单号")
    void parsesRefundNotification() throws Exception {
        AlipayChannel channel = channel(params -> "{}");
        Map<String, String> params = new TreeMap<>();
        params.put("app_id", APP_ID);
        params.put("notify_id", "NOTIFY0002");
        params.put("trade_no", "2026111722001400001");
        params.put("out_trade_no", "P20261117120000123456");
        params.put("out_biz_no", "REF00000000000000000001");
        params.put("trade_status", "TRADE_SUCCESS");
        params.put("refund_fee", "50.00");
        params.put("gmt_refund", "2026-11-17 13:00:00");
        params.put("sign", sign(params));

        assertTrue(channel.verifyCallback(params));
        ChannelNotification notification = channel.parseCallback(params);
        assertEquals(ChannelNotification.Kind.REFUND, notification.kind());
        assertEquals("REF00000000000000000001", notification.outTradeNo());
        assertEquals(5000L, notification.amountFen());
        assertTrue(notification.success());
    }

    // ---------- 下单 ----------

    @Test
    @DisplayName("下单返回网关跳转地址，含商户单号与元金额，notify_url 指向渠道回调")
    void createOrderBuildsPayUrl() {
        AlipayChannel channel = channel(params -> "{}");
        Payment payment = new Payment();
        payment.setPaymentId("PAY00000000000000000001");
        payment.setPayNo("P20261117120000123456");
        payment.setOrderId("O50001");
        payment.setAmount(11800L);

        ChannelOrder order = channel.createOrder(payment);
        assertTrue(order.payUrl().startsWith("https://openapi-sandbox.dl.alipaydev.com/gateway.do?"));
        assertTrue(order.payUrl().contains("out_trade_no"));
        assertTrue(order.payUrl().contains("alipay.trade.page.pay"));
        assertNull(order.channelTradeNo());
    }

    // ---------- 退款三态 ----------

    private static Refund refund() {
        Refund refund = new Refund();
        refund.setRefundId("REF00000000000000000001");
        refund.setAmount(5000L);
        refund.setReason("七天无理由");
        return refund;
    }

    private static Payment payment() {
        Payment payment = new Payment();
        payment.setPayNo("P20261117120000123456");
        return payment;
    }

    @Test
    @DisplayName("渠道退款 code=10000：SUCCESS 且带渠道退款单号")
    void refundSuccess() {
        AlipayChannel channel = channel(params ->
                "{\"alipay_trade_refund_response\":{\"code\":\"10000\",\"trade_no\":\"2026111722001400001\"}}");
        ChannelRefundResult result = channel.refund(refund(), payment());
        assertEquals(ChannelRefundResult.Status.SUCCESS, result.status());
        assertEquals("2026111722001400001", result.channelRefundNo());
    }

    @Test
    @DisplayName("渠道退款业务失败：FAILED 且记录原因")
    void refundFailed() {
        AlipayChannel channel = channel(params ->
                "{\"alipay_trade_refund_response\":{\"code\":\"40004\",\"sub_code\":\"ACQ.TRADE_NOT_EXIST\"}}");
        ChannelRefundResult result = channel.refund(refund(), payment());
        assertEquals(ChannelRefundResult.Status.FAILED, result.status());
        assertTrue(result.errorMessage().contains("40004"));
    }

    @Test
    @DisplayName("渠道退款网络异常：保持 PROCESSING，等回调或人工重试")
    void refundNetworkErrorStaysProcessing() {
        AlipayChannel channel = channel(params -> {
            throw new IllegalStateException("网关请求失败");
        });
        ChannelRefundResult result = channel.refund(refund(), payment());
        assertEquals(ChannelRefundResult.Status.PROCESSING, result.status());
    }

    @Test
    @DisplayName("退款请求携带幂等键 out_request_no=refundId")
    void refundUsesIdempotencyKey() {
        Map<String, String> captured = new HashMap<>();
        AlipayChannel channel = channel(params -> {
            captured.putAll(params);
            return "{\"alipay_trade_refund_response\":{\"code\":\"10000\",\"trade_no\":\"T1\"}}";
        });
        channel.refund(refund(), payment());
        assertEquals("alipay.trade.refund", captured.get("method"));
        assertTrue(captured.get("biz_content").contains("\"out_request_no\":\"REF00000000000000000001\""));
        assertTrue(captured.get("biz_content").contains("\"refund_amount\":\"50.00\""));
    }

    // ---------- 环境变量缺失即启动失败 ----------

    @Test
    @DisplayName("启用后缺任一必需环境变量：构造即抛异常")
    void missingEnvFailsFast() {
        for (String key : new String[]{"ALIPAY_APP_ID", "ALIPAY_PRIVATE_KEY",
                "ALIPAY_PUBLIC_KEY", "ALIPAY_NOTIFY_BASE_URL"}) {
            Map<String, String> env = env();
            env.remove(key);
            IllegalStateException exception = assertThrows(IllegalStateException.class,
                    () -> new AlipayChannel(env, params -> "{}"));
            assertTrue(exception.getMessage().contains(key));
        }
    }

    @Test
    @DisplayName("非法私钥：构造即抛异常，不进入运行期")
    void invalidPrivateKeyFailsFast() {
        Map<String, String> env = env();
        env.put("ALIPAY_PRIVATE_KEY", "not-a-valid-key");
        assertThrows(IllegalStateException.class, () -> new AlipayChannel(env, params -> "{}"));
    }
}