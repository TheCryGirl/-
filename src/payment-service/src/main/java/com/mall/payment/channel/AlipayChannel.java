package com.mall.payment.channel;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mall.payment.common.ChannelType;
import com.mall.payment.entity.Payment;
import com.mall.payment.entity.Refund;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.Signature;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

/**
 * 支付宝沙箱渠道（#137）。
 *
 * <p>渠道参数只从环境变量读取：ALIPAY_APP_ID / ALIPAY_PRIVATE_KEY / ALIPAY_PUBLIC_KEY /
 * ALIPAY_NOTIFY_BASE_URL（可选 ALIPAY_GATEWAY_URL 覆盖沙箱网关）。
 * 开关 payment.alipay.enabled=true 时本 Bean 才创建；任一必需变量缺失则启动即失败。
 * 私钥与公钥不落库、不进配置文件、不打印日志。
 *
 * <p>签名为 RSA2（SHA256withRSA）：按参数名升序拼接 k=v& 后签名/验签。
 * 金额在渠道侧以「元」传输，进出各做一次换算，本地一律用分。
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "payment.alipay.enabled", havingValue = "true")
public class AlipayChannel implements PaymentChannel {

    private static final String DEFAULT_SANDBOX_GATEWAY =
            "https://openapi-sandbox.dl.alipaydev.com/gateway.do";
    private static final DateTimeFormatter TIMESTAMP_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final String SIGN_TYPE = "RSA2";
    private static final String CHARSET = "utf-8";

    private final String appId;
    private final String gatewayUrl;
    private final String notifyBaseUrl;
    private final PrivateKey privateKey;
    private final PublicKey alipayPublicKey;
    private final AlipayTransport transport;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public AlipayChannel() {
        this(System.getenv(), null);
    }

    AlipayChannel(Map<String, String> env, AlipayTransport transport) {
        this.appId = requireEnv(env, "ALIPAY_APP_ID");
        String privateKeyText = requireEnv(env, "ALIPAY_PRIVATE_KEY");
        String publicKeyText = requireEnv(env, "ALIPAY_PUBLIC_KEY");
        this.notifyBaseUrl = stripTrailingSlash(requireEnv(env, "ALIPAY_NOTIFY_BASE_URL"));
        String gateway = env.get("ALIPAY_GATEWAY_URL");
        this.gatewayUrl = gateway == null || gateway.isBlank() ? DEFAULT_SANDBOX_GATEWAY : gateway;
        this.privateKey = parsePrivateKey(privateKeyText);
        this.alipayPublicKey = parsePublicKey(publicKeyText);
        this.transport = transport != null ? transport : defaultTransport(this.gatewayUrl);
    }

    @Override
    public ChannelType type() {
        return ChannelType.ALIPAY;
    }

    @Override
    public ChannelOrder createOrder(Payment payment) {
        String bizContent = toJson(Map.of(
                "out_trade_no", payment.getPayNo(),
                "total_amount", fenToYuan(payment.getAmount()),
                "subject", "商城订单 " + payment.getOrderId(),
                "product_code", "FAST_INSTANT_TRADE_PAY"));
        Map<String, String> params = commonParams("alipay.trade.page.pay", bizContent);
        params.put("notify_url", notifyBaseUrl + "/callback/alipay");
        params.put("sign", sign(params));
        String query = params.entrySet().stream()
                .map(e -> urlEncode(e.getKey()) + "=" + urlEncode(e.getValue()))
                .collect(Collectors.joining("&"));
        return new ChannelOrder(gatewayUrl + "?" + query, null);
    }

    @Override
    public ChannelNotification queryOrder(Payment payment) {
        String bizContent = toJson(Map.of("out_trade_no", payment.getPayNo()));
        JsonNode response = invoke("alipay.trade.query", bizContent, "alipay_trade_query_response");
        String code = response.path("code").asText();
        if (!"10000".equals(code)) {
            throw new IllegalStateException("支付宝查单失败, code=" + code
                    + ", subCode=" + response.path("sub_code").asText(""));
        }
        String tradeStatus = response.path("trade_status").asText();
        boolean success = "TRADE_SUCCESS".equals(tradeStatus) || "TRADE_FINISHED".equals(tradeStatus);
        return new ChannelNotification(
                ChannelNotification.Kind.PAYMENT,
                response.path("trade_no").asText(null),
                response.path("trade_no").asText(null),
                payment.getPayNo(),
                yuanToFen(response.path("total_amount").asText("0")),
                success,
                null,
                response.path("send_pay_date").asText(null));
    }

    @Override
    public boolean verifyCallback(Map<String, String> params) {
        String sign = params.get("sign");
        if (sign == null || sign.isBlank()) {
            return false;
        }
        if (!appId.equals(params.get("app_id"))) {
            log.error("支付宝回调 app_id 不匹配，拒绝处理");
            return false;
        }
        String content = signContent(params);
        try {
            Signature verifier = Signature.getInstance("SHA256withRSA");
            verifier.initVerify(alipayPublicKey);
            verifier.update(content.getBytes(StandardCharsets.UTF_8));
            return verifier.verify(Base64.getDecoder().decode(sign));
        } catch (Exception e) {
            log.error("支付宝回调验签异常, outTradeNo={}", params.get("out_trade_no"));
            return false;
        }
    }

    @Override
    public ChannelNotification parseCallback(Map<String, String> params) {
        String tradeStatus = params.get("trade_status");
        boolean success = "TRADE_SUCCESS".equals(tradeStatus) || "TRADE_FINISHED".equals(tradeStatus);
        String refundFee = params.get("refund_fee");
        if (refundFee != null && !refundFee.isBlank()) {
            // 退款通知：out_biz_no 即退款请求号 out_request_no（本地 refundId）
            String outBizNo = params.get("out_biz_no");
            return new ChannelNotification(
                    ChannelNotification.Kind.REFUND,
                    params.get("notify_id"),
                    params.get("trade_no"),
                    outBizNo != null && !outBizNo.isBlank() ? outBizNo : params.get("out_trade_no"),
                    yuanToFen(refundFee),
                    success,
                    params.get("trade_no"),
                    params.get("gmt_refund"));
        }
        return new ChannelNotification(
                ChannelNotification.Kind.PAYMENT,
                params.get("notify_id"),
                params.get("trade_no"),
                params.get("out_trade_no"),
                yuanToFen(params.get("total_amount")),
                success,
                null,
                params.get("gmt_payment"));
    }

    @Override
    public ChannelRefundResult refund(Refund refund, Payment payment) {
        String bizContent = toJson(Map.of(
                "out_trade_no", payment.getPayNo(),
                "refund_amount", fenToYuan(refund.getAmount()),
                "out_request_no", refund.getRefundId(),
                "refund_reason", refund.getReason() == null ? "售后退款" : refund.getReason()));
        JsonNode response;
        try {
            response = invoke("alipay.trade.refund", bizContent, "alipay_trade_refund_response");
        } catch (Exception e) {
            // 网络异常时渠道可能已受理，保持 PROCESSING 等回调或人工重试（同一 out_request_no 幂等）
            log.error("支付宝退款请求异常，保持处理中, refundId={}", refund.getRefundId());
            return ChannelRefundResult.processing();
        }
        String code = response.path("code").asText();
        if ("10000".equals(code)) {
            return ChannelRefundResult.success(response.path("trade_no").asText(null));
        }
        String subCode = response.path("sub_code").asText("");
        log.error("支付宝退款失败, refundId={}, code={}, subCode={}", refund.getRefundId(), code, subCode);
        return ChannelRefundResult.failed("渠道退款失败: " + code + " " + subCode);
    }

    @Override
    public ChannelRefundResult queryRefund(Refund refund, Payment payment) {
        String bizContent = toJson(Map.of(
                "out_trade_no", payment.getPayNo(),
                "out_request_no", refund.getRefundId()));
        JsonNode response;
        try {
            response = invoke("alipay.trade.fastpay.refund.query", bizContent,
                    "alipay_trade_fastpay_refund_query_response");
        } catch (Exception e) {
            log.error("支付宝退款查询异常, refundId={}", refund.getRefundId());
            return ChannelRefundResult.processing();
        }
        String code = response.path("code").asText();
        if ("10000".equals(code) && "REFUND_SUCCESS".equals(response.path("refund_status").asText())) {
            String channelRefundNo = response.path("trade_no").asText(null);
            return ChannelRefundResult.success(
                    channelRefundNo != null ? channelRefundNo : refund.getChannelRefundNo());
        }
        return ChannelRefundResult.processing();
    }

    /* ---------- 内部工具 ---------- */

    private Map<String, String> commonParams(String method, String bizContent) {
        Map<String, String> params = new TreeMap<>();
        params.put("app_id", appId);
        params.put("method", method);
        params.put("charset", CHARSET);
        params.put("sign_type", SIGN_TYPE);
        params.put("timestamp", LocalDateTime.now().format(TIMESTAMP_FORMAT));
        params.put("version", "1.0");
        params.put("biz_content", bizContent);
        return params;
    }

    private JsonNode invoke(String method, String bizContent, String responseNode) {
        Map<String, String> params = commonParams(method, bizContent);
        params.put("sign", sign(params));
        String body = transport.post(params);
        try {
            JsonNode root = objectMapper.readTree(body);
            JsonNode node = root.path(responseNode);
            if (node.isMissingNode()) {
                throw new IllegalStateException("支付宝响应缺少 " + responseNode);
            }
            return node;
        } catch (IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("支付宝响应解析失败", e);
        }
    }

    private String sign(Map<String, String> params) {
        try {
            Signature signer = Signature.getInstance("SHA256withRSA");
            signer.initSign(privateKey);
            signer.update(signContent(params).getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(signer.sign());
        } catch (Exception e) {
            throw new IllegalStateException("支付宝签名失败", e);
        }
    }

    /** 按参数名升序拼接 k=v&，剔除 sign/sign_type 与空值；值不做 URL 编码 */
    static String signContent(Map<String, String> params) {
        return params.entrySet().stream()
                .filter(e -> !"sign".equals(e.getKey()) && !"sign_type".equals(e.getKey()))
                .filter(e -> e.getValue() != null && !e.getValue().isEmpty())
                .sorted(Map.Entry.comparingByKey())
                .map(e -> e.getKey() + "=" + e.getValue())
                .collect(Collectors.joining("&"));
    }

    static long yuanToFen(String yuan) {
        if (yuan == null || yuan.isBlank()) {
            return 0;
        }
        return new BigDecimal(yuan).movePointRight(2)
                .setScale(0, RoundingMode.HALF_UP).longValueExact();
    }

    static String fenToYuan(long fen) {
        return BigDecimal.valueOf(fen).movePointLeft(2).toPlainString();
    }

    private String toJson(Map<String, String> value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            throw new IllegalStateException("biz_content 序列化失败", e);
        }
    }

    private static String urlEncode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private static String stripTrailingSlash(String value) {
        return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }

    private static String requireEnv(Map<String, String> env, String name) {
        String value = env.get(name);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(
                    "支付宝沙箱已启用但环境变量 " + name + " 缺失，请配置后再启动（密钥只从环境变量读取）");
        }
        return value;
    }

    private static PrivateKey parsePrivateKey(String base64) {
        try {
            byte[] bytes = Base64.getDecoder().decode(stripKeyWhitespace(base64));
            return KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(bytes));
        } catch (Exception e) {
            throw new IllegalStateException("ALIPAY_PRIVATE_KEY 不是合法的 PKCS8 Base64 私钥");
        }
    }

    private static PublicKey parsePublicKey(String base64) {
        try {
            byte[] bytes = Base64.getDecoder().decode(stripKeyWhitespace(base64));
            return KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(bytes));
        } catch (Exception e) {
            throw new IllegalStateException("ALIPAY_PUBLIC_KEY 不是合法的 X509 Base64 公钥");
        }
    }

    private static String stripKeyWhitespace(String key) {
        return key.replaceAll("-----[A-Z ]+-----", "").replaceAll("\\s", "");
    }

    private static AlipayTransport defaultTransport(String gatewayUrl) {
        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();
        return formParams -> {
            String form = formParams.entrySet().stream()
                    .map(e -> urlEncode(e.getKey()) + "=" + urlEncode(e.getValue()))
                    .collect(Collectors.joining("&"));
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(gatewayUrl))
                    .timeout(Duration.ofSeconds(10))
                    .header("Content-Type", "application/x-www-form-urlencoded;charset=utf-8")
                    .POST(HttpRequest.BodyPublishers.ofString(form, StandardCharsets.UTF_8))
                    .build();
            try {
                HttpResponse<String> response =
                        client.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
                return response.body();
            } catch (Exception e) {
                throw new IllegalStateException("支付宝网关请求失败", e);
            }
        };
    }
}