package com.mall.payment.service;

import com.mall.payment.client.OrderClient;
import com.mall.payment.client.ProductClient;
import com.mall.payment.common.BizException;
import com.mall.payment.common.ErrorCode;
import com.mall.payment.dto.request.RefundRequest;
import com.mall.payment.dto.response.RefundResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@SpringBootTest
class RefundServiceIntegrationTest {

    private static final String PAYMENT_ID = "PAYTEST0000000000002";
    private static final String PAY_NO = "P20261020000002";
    private static final String ORDER_ID = "O50002";
    private static final String BUYER_ID = "U10001";
    private static final long PAID_AMOUNT = 11800L;

    @Autowired
    private RefundService refundService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @MockBean
    private OrderClient orderClient;

    @MockBean
    private ProductClient productClient;

    @BeforeEach
    void cleanDatabase() {
        jdbcTemplate.update("DELETE FROM refund");
        jdbcTemplate.update("DELETE FROM payment_callback");
        jdbcTemplate.update("DELETE FROM payment");
        insertPaidPayment();
    }

    @Test
    void rejectsRefundExceedingPaidAmount() {
        RefundResponse first = refundService.createRefund(BUYER_ID, PAYMENT_ID, refund(5000L));

        BizException exception = assertThrows(BizException.class,
                () -> refundService.createRefund(BUYER_ID, PAYMENT_ID, refund(7000L)));

        assertEquals(ErrorCode.REFUND_EXCEEDS_PAID, exception.getCode());
        assertEquals("PENDING", first.getStatus().name());
        assertEquals(5000L, refundedAmount());
        assertEquals(1, count("refund"));
    }

    @Test
    void paymentBecomesRefundedOnlyAfterApprovedAmountReachesPaidAmount() {
        RefundResponse first = refundService.createRefund(BUYER_ID, PAYMENT_ID, refund(5000L));
        RefundResponse second = refundService.createRefund(BUYER_ID, PAYMENT_ID, refund(6800L));

        assertEquals(11800L, refundedAmount());
        refundService.auditRefund(first.getRefundId(), true, "SELLER01", "同意");
        assertEquals("SUCCESS", paymentStatus());

        refundService.auditRefund(second.getRefundId(), true, "SELLER01", "同意");
        assertEquals("REFUNDED", paymentStatus());

        BizException exception = assertThrows(BizException.class,
                () -> refundService.createRefund(BUYER_ID, PAYMENT_ID, refund(1L)));
        assertEquals(ErrorCode.REFUND_EXCEEDS_PAID, exception.getCode());
    }

    @Test
    void rejectedRefundReleasesReservedAmount() {
        RefundResponse refund = refundService.createRefund(BUYER_ID, PAYMENT_ID, refund(5000L));
        refundService.auditRefund(refund.getRefundId(), false, "SELLER01", "不符合条件");

        assertEquals(0L, refundedAmount());
        assertEquals("SUCCESS", paymentStatus());

        RefundResponse full = refundService.createRefund(BUYER_ID, PAYMENT_ID, refund(11800L));
        refundService.auditRefund(full.getRefundId(), true, "SELLER01", "同意");

        assertEquals(11800L, refundedAmount());
        assertEquals("REFUNDED", paymentStatus());
    }

    @Test
    void refundCannotBeAuditedTwice() {
        RefundResponse refund = refundService.createRefund(BUYER_ID, PAYMENT_ID, refund(5000L));
        refundService.auditRefund(refund.getRefundId(), true, "SELLER01", "同意");

        BizException exception = assertThrows(BizException.class,
                () -> refundService.auditRefund(refund.getRefundId(), false, "SELLER01", "重复审核"));

        assertEquals(ErrorCode.PARAM_INVALID, exception.getCode());
    }

    @Test
    void otherBuyerCannotOperatePayment() {
        BizException exception = assertThrows(BizException.class,
                () -> refundService.createRefund("U99999", PAYMENT_ID, refund(1000L)));

        assertEquals(ErrorCode.FORBIDDEN, exception.getCode());
        assertEquals(0, count("refund"));
    }

    private void insertPaidPayment() {
        jdbcTemplate.update("""
                INSERT INTO payment (
                    payment_id, pay_no, order_id, buyer_id, channel, amount,
                    refunded_amount, status, paid_at, created_by, updated_by, is_deleted
                ) VALUES (?, ?, ?, ?, 'MOCK', ?, 0, 'SUCCESS', CURRENT_TIMESTAMP, ?, ?, 0)
                """, PAYMENT_ID, PAY_NO, ORDER_ID, BUYER_ID, PAID_AMOUNT, BUYER_ID, BUYER_ID);
    }

    private RefundRequest refund(long amount) {
        RefundRequest request = new RefundRequest();
        request.setAmount(amount);
        request.setReason("测试退款");
        return request;
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

    private int count(String table) {
        Integer count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM " + table, Integer.class);
        return count == null ? 0 : count;
    }
}
