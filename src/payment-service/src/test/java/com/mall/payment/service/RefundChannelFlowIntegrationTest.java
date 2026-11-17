package com.mall.payment.service;

import com.mall.payment.channel.ChannelNotification;
import com.mall.payment.channel.ChannelRefundResult;
import com.mall.payment.channel.ControllableAlipayChannel;
import com.mall.payment.client.OrderClient;
import com.mall.payment.client.ProductClient;
import com.mall.payment.client.dto.StockConfirmRequest;
import com.mall.payment.common.BizException;
import com.mall.payment.common.ChannelType;
import com.mall.payment.common.ErrorCode;
import com.mall.payment.common.RefundStatus;
import com.mall.payment.common.Result;
import com.mall.payment.dto.request.RefundRequest;
import com.mall.payment.dto.response.RefundResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 非 MOCK 渠道退款状态机集成测试（#137）。
 *
 * <p>用可控的假支付宝渠道驱动 PROCESSING → SUCCESS/FAILED 的真实落库链路：
 * 审核置 PROCESSING、事务提交后执行器调渠道、终态条件 UPDATE、全额退款通知、
 * FAILED 保留预占额度、人工重试复用预占不重复退款、PROCESSING 等回调收口。
 */
@SpringBootTest
@Import(RefundChannelFlowIntegrationTest.FakeChannelConfig.class)
class RefundChannelFlowIntegrationTest {

    private static final String PAYMENT_ID = "PAYCHAN0000000000001";
    private static final String PAY_NO = "P20261117000001";
    private static final String ORDER_ID = "O60001";
    private static final String BUYER_ID = "U10001";
    private static final String SELLER_ID = "S10001";
    private static final long PAID_AMOUNT = 11800L;

    @TestConfiguration
    static class FakeChannelConfig {
        @Bean
        ControllableAlipayChannel controllableAlipayChannel() {
            return new ControllableAlipayChannel();
        }
    }

    @Autowired
    private RefundService refundService;
    @Autowired
    private RefundCallbackService refundCallbackService;
    @Autowired
    private ControllableAlipayChannel fakeChannel;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    @MockBean
    private OrderClient orderClient;
    @MockBean
    private ProductClient productClient;

    @BeforeEach
    void setUp() {
        jdbcTemplate.update("DELETE FROM refund_notification_task");
        jdbcTemplate.update("DELETE FROM after_sale_ticket");
        jdbcTemplate.update("DELETE FROM refund_callback");
        jdbcTemplate.update("DELETE FROM refund");
        jdbcTemplate.update("DELETE FROM payment_callback");
        jdbcTemplate.update("DELETE FROM payment");
        insertPaidPayment(PAYMENT_ID, PAY_NO, ORDER_ID, "ALIPAY", PAID_AMOUNT);
        fakeChannel.reset();
        when(orderClient.markRefunded(ORDER_ID)).thenReturn(Result.success(null));
        when(productClient.refundRestock(any(StockConfirmRequest.class))).thenReturn(Result.success(null));
    }

    @Test
    @DisplayName("全额退款：审核置 PROCESSING，渠道成功后退款 SUCCESS、支付单 REFUNDED、两条通知任务")
    void fullRefundSuccessViaChannel() {
        fakeChannel.setNextResult(ChannelRefundResult.success("ALIPAYRF-001"));
        RefundResponse created = refundService.createRefund(BUYER_ID, PAYMENT_ID, refund(PAID_AMOUNT));

        RefundResponse audited = refundService.auditRefund(created.getRefundId(), true, SELLER_ID, "同意");

        // 审核响应是事务内快照（PROCESSING）；渠道在事务提交后异步收口，终态读库
        assertEquals(RefundStatus.PROCESSING, audited.getStatus());
        RefundResponse settled = reload(created.getRefundId());
        assertEquals(RefundStatus.SUCCESS, settled.getStatus());
        assertEquals("ALIPAYRF-001", settled.getChannelRefundNo());
        assertNotNull(settled.getRefundedAt());
        assertEquals("REFUNDED", paymentStatus());
        assertEquals(PAID_AMOUNT, refundedAmount());
        assertEquals(1, fakeChannel.refundCalls());
        assertEquals(2, count("refund_notification_task"));
        verify(orderClient, times(1)).markRefunded(ORDER_ID);
        verify(productClient, times(1)).refundRestock(any(StockConfirmRequest.class));
    }

    @Test
    @DisplayName("部分退款成功：支付单保持 SUCCESS，不触发订单通知")
    void partialRefundSuccessKeepsPaymentSuccess() {
        fakeChannel.setNextResult(ChannelRefundResult.success("ALIPAYRF-002"));
        RefundResponse created = refundService.createRefund(BUYER_ID, PAYMENT_ID, refund(5000L));

        RefundResponse audited = refundService.auditRefund(created.getRefundId(), true, SELLER_ID, "同意");

        assertEquals(RefundStatus.PROCESSING, audited.getStatus());
        assertEquals(RefundStatus.SUCCESS, reload(created.getRefundId()).getStatus());
        assertEquals("SUCCESS", paymentStatus());
        assertEquals(5000L, refundedAmount());
        assertEquals(0, count("refund_notification_task"));
        verify(orderClient, never()).markRefunded(ORDER_ID);
    }

    @Test
    @DisplayName("渠道退款失败：退款 FAILED，预占额度保留，支付单仍 SUCCESS，记录失败原因")
    void channelFailedKeepsReservation() {
        fakeChannel.setNextResult(ChannelRefundResult.failed("渠道拒绝"));
        RefundResponse created = refundService.createRefund(BUYER_ID, PAYMENT_ID, refund(5000L));

        RefundResponse audited = refundService.auditRefund(created.getRefundId(), true, SELLER_ID, "同意");

        assertEquals(RefundStatus.PROCESSING, audited.getStatus());
        RefundResponse settled = reload(created.getRefundId());
        assertEquals(RefundStatus.FAILED, settled.getStatus());
        assertEquals("渠道拒绝", settled.getChannelError());
        assertEquals("SUCCESS", paymentStatus());
        // 预占额度保留，供人工重试复用
        assertEquals(5000L, refundedAmount());
        assertEquals(0, count("refund_notification_task"));
        verify(orderClient, never()).markRefunded(ORDER_ID);
    }

    @Test
    @DisplayName("人工重试 FAILED 退款：复用预占额度，不重复预占，渠道成功后退款 SUCCESS")
    void retryFailedRefundReusesReservation() {
        fakeChannel.setNextResult(ChannelRefundResult.failed("渠道拒绝"));
        RefundResponse created = refundService.createRefund(BUYER_ID, PAYMENT_ID, refund(5000L));
        refundService.auditRefund(created.getRefundId(), true, SELLER_ID, "同意");
        assertEquals(5000L, refundedAmount());
        assertEquals(1, fakeChannel.refundCalls());

        fakeChannel.setNextResult(ChannelRefundResult.success("ALIPAYRF-RETRY"));
        RefundResponse retried = refundService.retryRefund(created.getRefundId(), SELLER_ID, "SELLER");

        assertEquals(RefundStatus.PROCESSING, retried.getStatus());
        RefundResponse settled = reload(created.getRefundId());
        assertEquals(RefundStatus.SUCCESS, settled.getStatus());
        assertEquals("ALIPAYRF-RETRY", settled.getChannelRefundNo());
        // 额度仍是创建时预占的 5000，没有第二次预占
        assertEquals(5000L, refundedAmount());
        assertEquals(2, fakeChannel.refundCalls());
        assertEquals("SUCCESS", paymentStatus());
    }

    @Test
    @DisplayName("渠道未返回终态：退款保持 PROCESSING，退款回调成功后收口")
    void channelProcessingWaitsForCallback() {
        fakeChannel.setNextResult(ChannelRefundResult.processing());
        RefundResponse created = refundService.createRefund(BUYER_ID, PAYMENT_ID, refund(PAID_AMOUNT));

        RefundResponse audited = refundService.auditRefund(created.getRefundId(), true, SELLER_ID, "同意");
        assertEquals(RefundStatus.PROCESSING, audited.getStatus());
        assertEquals(PAID_AMOUNT, refundedAmount());
        assertEquals("SUCCESS", paymentStatus());
        verify(orderClient, never()).markRefunded(ORDER_ID);

        ChannelNotification notification = new ChannelNotification(
                ChannelNotification.Kind.REFUND, "NOTIFY-PROCESSING-1", "ALIPAYTRADE1",
                created.getRefundId(), PAID_AMOUNT, true, "ALIPAYRF-CB", "2026-11-17 12:00:00");
        ChannelCallbackOutcome outcome =
                refundCallbackService.processVerified(ChannelType.ALIPAY, notification, Map.of());

        assertTrue(outcome.success());
        assertEquals("SUCCESS", refundStatus(created.getRefundId()));
        assertEquals("REFUNDED", paymentStatus());
        verify(orderClient, times(1)).markRefunded(ORDER_ID);
    }

    @Test
    @DisplayName("渠道调用抛异常：退款保持 PROCESSING，不回滚预占，可人工重试")
    void channelExceptionStaysProcessing() {
        fakeChannel.setThrowOnRefund(true);
        RefundResponse created = refundService.createRefund(BUYER_ID, PAYMENT_ID, refund(5000L));

        RefundResponse audited = refundService.auditRefund(created.getRefundId(), true, SELLER_ID, "同意");

        assertEquals(RefundStatus.PROCESSING, audited.getStatus());
        assertEquals(5000L, refundedAmount());
        assertEquals(1, fakeChannel.refundCalls());

        fakeChannel.setThrowOnRefund(false);
        fakeChannel.setNextResult(ChannelRefundResult.success("ALIPAYRF-RECOVER"));
        RefundResponse retried = refundService.retryRefund(created.getRefundId(), SELLER_ID, "SELLER");
        assertEquals(RefundStatus.PROCESSING, retried.getStatus());
        assertEquals("SUCCESS", refundStatus(created.getRefundId()));
        assertEquals(2, fakeChannel.refundCalls());
    }

    @Test
    @DisplayName("驳回退款释放预占；驳回后不可重试")
    void rejectedRefundReleasesAndCannotRetry() {
        RefundResponse created = refundService.createRefund(BUYER_ID, PAYMENT_ID, refund(5000L));
        refundService.auditRefund(created.getRefundId(), false, SELLER_ID, "不符合条件");
        assertEquals(0L, refundedAmount());
        assertEquals(0, fakeChannel.refundCalls());

        BizException exception = assertThrows(BizException.class,
                () -> refundService.retryRefund(created.getRefundId(), SELLER_ID, "SELLER"));
        assertEquals(ErrorCode.PARAM_INVALID, exception.getCode());
    }

    @Test
    @DisplayName("他人卖家无权重试")
    void retryForbiddenForOtherSeller() {
        fakeChannel.setNextResult(ChannelRefundResult.failed("渠道拒绝"));
        RefundResponse created = refundService.createRefund(BUYER_ID, PAYMENT_ID, refund(5000L));
        refundService.auditRefund(created.getRefundId(), true, SELLER_ID, "同意");

        BizException exception = assertThrows(BizException.class,
                () -> refundService.retryRefund(created.getRefundId(), "S99999", "SELLER"));
        assertEquals(ErrorCode.FORBIDDEN, exception.getCode());
    }

    @Test
    @DisplayName("MOCK 渠道仍审核同事务 SUCCESS，并写入派生渠道退款单号")
    void mockChannelStillInlineSuccess() {
        String mockPaymentId = "PAYCHAN0000000000002";
        insertPaidPayment(mockPaymentId, "P20261117000002", "O60002", "MOCK", 5000L);
        RefundResponse created = refundService.createRefund(BUYER_ID, mockPaymentId, refund(5000L));

        RefundResponse audited = refundService.auditRefund(created.getRefundId(), true, SELLER_ID, "同意");

        assertEquals(RefundStatus.SUCCESS, audited.getStatus());
        assertEquals("MOCKRF-" + created.getRefundId(), audited.getChannelRefundNo());
        // MOCK 不走异步执行器
        assertEquals(0, fakeChannel.refundCalls());
    }

    private RefundResponse reload(String refundId) {
        return refundService.getRefund(BUYER_ID, "BUYER", refundId);
    }

    private RefundRequest refund(long amount) {
        RefundRequest request = new RefundRequest();
        request.setAmount(amount);
        request.setReason("渠道退款测试");
        return request;
    }

    private void insertPaidPayment(String paymentId, String payNo, String orderId,
                                   String channel, long amount) {
        jdbcTemplate.update("""
                INSERT INTO payment (
                    payment_id, pay_no, order_id, buyer_id, seller_id, channel, amount,
                    refunded_amount, status, paid_at, created_by, updated_by, is_deleted
                ) VALUES (?, ?, ?, ?, ?, ?, ?, 0, 'SUCCESS', CURRENT_TIMESTAMP, ?, ?, 0)
                """, paymentId, payNo, orderId, BUYER_ID, SELLER_ID, channel, amount, BUYER_ID, BUYER_ID);
    }

    private String paymentStatus() {
        return jdbcTemplate.queryForObject(
                "SELECT status FROM payment WHERE payment_id = ?", String.class, PAYMENT_ID);
    }

    private long refundedAmount() {
        Long value = jdbcTemplate.queryForObject(
                "SELECT refunded_amount FROM payment WHERE payment_id = ?", Long.class, PAYMENT_ID);
        return value == null ? 0 : value;
    }

    private String refundStatus(String refundId) {
        return jdbcTemplate.queryForObject(
                "SELECT status FROM refund WHERE refund_id = ?", String.class, refundId);
    }

    private int count(String table) {
        Integer count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM " + table, Integer.class);
        return count == null ? 0 : count;
    }
}