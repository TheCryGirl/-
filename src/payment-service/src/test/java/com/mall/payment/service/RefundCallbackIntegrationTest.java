package com.mall.payment.service;

import com.mall.payment.channel.ChannelNotification;
import com.mall.payment.client.OrderClient;
import com.mall.payment.client.ProductClient;
import com.mall.payment.client.dto.StockConfirmRequest;
import com.mall.payment.common.ChannelType;
import com.mall.payment.common.ErrorCode;
import com.mall.payment.common.Result;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * 退款异步回调集成测试（#137）。
 *
 * <p>验签在编排层之前完成，这里直接驱动 {@link RefundCallbackService#processVerified}：
 * uk_channel_notify 唯一键幂等（不先查再插）、金额不一致只记流水拒绝、
 * 只收口 PROCESSING、迟到回调对 FAILED/SUCCESS 不改状态。
 */
@SpringBootTest
class RefundCallbackIntegrationTest {

    private static final String PAYMENT_ID = "PAYRCB0000000000001";
    private static final String PAY_NO = "P20261117100001";
    private static final String ORDER_ID = "O70001";
    private static final String REFUND_ID = "REFRCB0000000000000001";
    private static final String BUYER_ID = "U10001";
    private static final String SELLER_ID = "S10001";
    private static final long AMOUNT = 11800L;

    @Autowired
    private RefundCallbackService refundCallbackService;
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
        insertPayment("SUCCESS", AMOUNT);
        insertRefund("PROCESSING", AMOUNT);
        when(orderClient.markRefunded(ORDER_ID)).thenReturn(Result.success(null));
        when(productClient.refundRestock(any(StockConfirmRequest.class))).thenReturn(Result.success(null));
    }

    private ChannelNotification notification(String notifyId, long amountFen, boolean success) {
        return new ChannelNotification(ChannelNotification.Kind.REFUND, notifyId, "ALIPAYTRADE1",
                REFUND_ID, amountFen, success, "ALIPAYRF-" + notifyId, "2026-11-17 12:00:00");
    }

    @Test
    @DisplayName("PROCESSING 退款收到成功回调：置 SUCCESS，全额退款支付单 REFUNDED")
    void successCallbackCompletesProcessing() {
        ChannelCallbackOutcome outcome = refundCallbackService.processVerified(
                ChannelType.ALIPAY, notification("N1", AMOUNT, true), Map.of("k", "v"));

        assertTrue(outcome.success());
        assertFalse(outcome.duplicate());
        assertEquals("SUCCESS", refundStatus());
        assertEquals("REFUNDED", paymentStatus());
        assertEquals("PROCESSED", callbackStatus("N1"));
    }

    @Test
    @DisplayName("同一 notify_id 重复回调：唯一键去重，第二次 duplicate，退款只处理一次")
    void duplicateNotifyIdIsIdempotent() {
        ChannelCallbackOutcome first = refundCallbackService.processVerified(
                ChannelType.ALIPAY, notification("N2", AMOUNT, true), Map.of());
        ChannelCallbackOutcome second = refundCallbackService.processVerified(
                ChannelType.ALIPAY, notification("N2", AMOUNT, true), Map.of());

        assertTrue(first.success());
        assertFalse(first.duplicate());
        assertTrue(second.success());
        assertTrue(second.duplicate());
        assertEquals(1, count("refund_callback"));
        assertEquals("SUCCESS", refundStatus());
    }

    @Test
    @DisplayName("回调金额与退款单不一致：拒绝并记 AMOUNT_MISMATCH 流水，退款单不变")
    void amountMismatchRejected() {
        ChannelCallbackOutcome outcome = refundCallbackService.processVerified(
                ChannelType.ALIPAY, notification("N3", 1L, true), Map.of());

        assertFalse(outcome.success());
        assertEquals(ErrorCode.AMOUNT_MISMATCH, outcome.code());
        assertEquals("PROCESSING", refundStatus());
        assertEquals("SUCCESS", paymentStatus());
        assertEquals("AMOUNT_MISMATCH", callbackStatus("N3"));
    }

    @Test
    @DisplayName("失败通知：PROCESSING 退款置 FAILED，保留预占额度")
    void failedNotificationMarksFailed() {
        ChannelCallbackOutcome outcome = refundCallbackService.processVerified(
                ChannelType.ALIPAY, notification("N4", AMOUNT, false), Map.of());

        assertTrue(outcome.success());
        assertEquals("FAILED", refundStatus());
        assertEquals("SUCCESS", paymentStatus());
        assertEquals(AMOUNT, refundedAmount());
    }

    @Test
    @DisplayName("迟到回调打到 FAILED 退款：duplicate，不改状态，等人工重试")
    void lateCallbackOnFailedIsDuplicate() {
        jdbcTemplate.update("UPDATE refund SET status = 'FAILED' WHERE refund_id = ?", REFUND_ID);

        ChannelCallbackOutcome outcome = refundCallbackService.processVerified(
                ChannelType.ALIPAY, notification("N5", AMOUNT, true), Map.of());

        assertTrue(outcome.duplicate());
        assertEquals("FAILED", refundStatus());
        assertEquals("DUPLICATE", callbackStatus("N5"));
    }

    @Test
    @DisplayName("回调打到已 SUCCESS 退款：duplicate，不重复通知订单")
    void callbackOnSuccessIsDuplicate() {
        jdbcTemplate.update("UPDATE refund SET status = 'SUCCESS' WHERE refund_id = ?", REFUND_ID);

        ChannelCallbackOutcome outcome = refundCallbackService.processVerified(
                ChannelType.ALIPAY, notification("N6", AMOUNT, true), Map.of());

        assertTrue(outcome.duplicate());
        assertEquals("SUCCESS", refundStatus());
        assertEquals(0, count("refund_notification_task"));
    }

    @Test
    @DisplayName("退款单不存在：拒绝")
    void unknownRefundFails() {
        ChannelNotification unknown = new ChannelNotification(ChannelNotification.Kind.REFUND,
                "N7", "T", "REFNOTEXIST000000000001", AMOUNT, true, "RF", null);
        ChannelCallbackOutcome outcome =
                refundCallbackService.processVerified(ChannelType.ALIPAY, unknown, Map.of());

        assertFalse(outcome.success());
        assertEquals(ErrorCode.PARAM_INVALID, outcome.code());
    }

    private void insertPayment(String status, long refundedAmount) {
        jdbcTemplate.update("""
                INSERT INTO payment (
                    payment_id, pay_no, order_id, buyer_id, seller_id, channel, amount,
                    refunded_amount, status, paid_at, created_by, updated_by, is_deleted
                ) VALUES (?, ?, ?, ?, ?, 'ALIPAY', ?, ?, ?, CURRENT_TIMESTAMP, ?, ?, 0)
                """, PAYMENT_ID, PAY_NO, ORDER_ID, BUYER_ID, SELLER_ID, AMOUNT, refundedAmount,
                status, BUYER_ID, BUYER_ID);
    }

    private void insertRefund(String status, long amount) {
        jdbcTemplate.update("""
                INSERT INTO refund (
                    refund_id, payment_id, order_id, buyer_id, seller_id, amount, reason,
                    status, created_by, updated_by, is_deleted
                ) VALUES (?, ?, ?, ?, ?, ?, '退款回调测试', ?, ?, ?, 0)
                """, REFUND_ID, PAYMENT_ID, ORDER_ID, BUYER_ID, SELLER_ID, amount, status, BUYER_ID, BUYER_ID);
    }

    private String refundStatus() {
        return jdbcTemplate.queryForObject(
                "SELECT status FROM refund WHERE refund_id = ?", String.class, REFUND_ID);
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

    private String callbackStatus(String notifyId) {
        return jdbcTemplate.queryForObject(
                "SELECT process_status FROM refund_callback WHERE notify_id = ?", String.class, notifyId);
    }

    private int count(String table) {
        Integer count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM " + table, Integer.class);
        return count == null ? 0 : count;
    }
}