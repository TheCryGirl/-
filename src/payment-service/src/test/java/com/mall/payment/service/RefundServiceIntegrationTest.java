package com.mall.payment.service;

import com.mall.payment.client.OrderClient;
import com.mall.payment.client.ProductClient;
import com.mall.payment.client.dto.OrderDTO;
import com.mall.payment.client.dto.StockConfirmRequest;
import com.mall.payment.common.BizException;
import com.mall.payment.common.ErrorCode;
import com.mall.payment.common.OrderStatus;
import com.mall.payment.common.PageResult;
import com.mall.payment.common.Result;
import com.mall.payment.dto.request.RefundRequest;
import com.mall.payment.dto.response.RefundResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SpringBootTest
class RefundServiceIntegrationTest {

    private static final String PAYMENT_ID = "PAYTEST0000000000002";
    private static final String PAY_NO = "P20261020000002";
    private static final String ORDER_ID = "O50002";
    private static final String BUYER_ID = "U10001";
    private static final String SELLER_ID = "S10001";
    private static final String OTHER_SELLER_ID = "S20002";
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
        jdbcTemplate.update("DELETE FROM after_sale_ticket");
        jdbcTemplate.update("DELETE FROM refund");
        jdbcTemplate.update("DELETE FROM payment_callback");
        jdbcTemplate.update("DELETE FROM payment");
        insertPaidPayment();
        when(orderClient.markRefunded(ORDER_ID)).thenReturn(Result.success(null));
        when(productClient.refundRestock(any(StockConfirmRequest.class))).thenReturn(Result.success(null));
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
        refundService.auditRefund(first.getRefundId(), true, SELLER_ID, "同意");
        assertEquals("SUCCESS", paymentStatus());
        verify(orderClient, never()).markRefunded(ORDER_ID);

        refundService.auditRefund(second.getRefundId(), true, SELLER_ID, "同意");
        assertEquals("REFUNDED", paymentStatus());
        verify(orderClient, times(1)).markRefunded(ORDER_ID);

        BizException exception = assertThrows(BizException.class,
                () -> refundService.createRefund(BUYER_ID, PAYMENT_ID, refund(1L)));
        assertEquals(ErrorCode.REFUND_EXCEEDS_PAID, exception.getCode());
    }

    @Test
    void rejectedRefundReleasesReservedAmount() {
        RefundResponse refund = refundService.createRefund(BUYER_ID, PAYMENT_ID, refund(5000L));
        refundService.auditRefund(refund.getRefundId(), false, SELLER_ID, "不符合条件");

        assertEquals(0L, refundedAmount());
        assertEquals("SUCCESS", paymentStatus());
        verify(orderClient, never()).markRefunded(ORDER_ID);

        RefundResponse full = refundService.createRefund(BUYER_ID, PAYMENT_ID, refund(11800L));
        refundService.auditRefund(full.getRefundId(), true, SELLER_ID, "同意");

        assertEquals(11800L, refundedAmount());
        assertEquals("REFUNDED", paymentStatus());
        verify(orderClient, times(1)).markRefunded(ORDER_ID);
    }

    @Test
    void refundCannotBeAuditedTwice() {
        RefundResponse refund = refundService.createRefund(BUYER_ID, PAYMENT_ID, refund(5000L));
        refundService.auditRefund(refund.getRefundId(), true, SELLER_ID, "同意");

        BizException exception = assertThrows(BizException.class,
                () -> refundService.auditRefund(refund.getRefundId(), false, SELLER_ID, "重复审核"));

        assertEquals(ErrorCode.PARAM_INVALID, exception.getCode());
        verify(orderClient, never()).markRefunded(ORDER_ID);
    }

    @Test
    void otherBuyerCannotOperatePayment() {
        BizException exception = assertThrows(BizException.class,
                () -> refundService.createRefund("U99999", PAYMENT_ID, refund(1000L)));

        assertEquals(ErrorCode.FORBIDDEN, exception.getCode());
        assertEquals(0, count("refund"));
    }

    @Test
    void listsRefundsWithBuyerAndSellerIsolation() {
        insertPaidPayment("PAYTEST0000000000003", "P20261020000003",
                "O50003", "U20002", OTHER_SELLER_ID, 5000L);
        refundService.createRefund(BUYER_ID, PAYMENT_ID, refund(1000L));
        refundService.createRefund(BUYER_ID, PAYMENT_ID, refund(2000L));
        refundService.createRefund(BUYER_ID, PAYMENT_ID, refund(3000L));
        RefundResponse otherBuyerRefund = refundService.createRefund(
                "U20002", "PAYTEST0000000000003", refund(500L));

        PageResult<RefundResponse> buyerPage = refundService.listRefunds(
                BUYER_ID, "BUYER", "PENDING", 1, 2);
        assertEquals(3, buyerPage.getTotal());
        assertEquals(2, buyerPage.getPages());
        assertEquals(2, buyerPage.getList().size());
        assertTrue(buyerPage.getList().stream()
                .allMatch(item -> BUYER_ID.equals(item.getBuyerId())));
        assertFalse(buyerPage.getList().stream()
                .anyMatch(item -> item.getRefundId().equals(otherBuyerRefund.getRefundId())));

        PageResult<RefundResponse> secondPage = refundService.listRefunds(
                BUYER_ID, "BUYER", "PENDING", 2, 2);
        assertEquals(1, secondPage.getList().size());

        PageResult<RefundResponse> sellerPage = refundService.listRefunds(
                SELLER_ID, "SELLER", "PENDING", 1, 20);
        assertEquals(3, sellerPage.getTotal());
        assertTrue(sellerPage.getList().stream()
                .allMatch(item -> SELLER_ID.equals(item.getSellerId())));

        PageResult<RefundResponse> otherSellerPage = refundService.listRefunds(
                OTHER_SELLER_ID, "SELLER", "PENDING", 1, 20);
        assertEquals(1, otherSellerPage.getTotal());
        assertEquals(otherBuyerRefund.getRefundId(), otherSellerPage.getList().get(0).getRefundId());

        PageResult<RefundResponse> adminPage = refundService.listRefunds(
                "ADMIN01", "ADMIN", "PENDING", 1, 20);
        assertEquals(4, adminPage.getTotal());

        PageResult<RefundResponse> successPage = refundService.listRefunds(
                BUYER_ID, "BUYER", "SUCCESS", 1, 20);
        assertEquals(0, successPage.getTotal());
        assertEquals(0, successPage.getPages());
        assertTrue(successPage.getList().isEmpty());

        BizException invalidStatus = assertThrows(BizException.class,
                () -> refundService.listRefunds(BUYER_ID, "BUYER", "UNKNOWN", 1, 20));
        assertEquals(ErrorCode.PARAM_INVALID, invalidStatus.getCode());
    }

    @Test
    void refundDetailAndAuditAreRestrictedToOwnerSeller() {
        RefundResponse ownerRefund = refundService.createRefund(BUYER_ID, PAYMENT_ID, refund(5000L));
        RefundResponse adminRefund = refundService.createRefund(BUYER_ID, PAYMENT_ID, refund(1000L));

        assertEquals(ownerRefund.getRefundId(),
                refundService.getRefund(BUYER_ID, "BUYER", ownerRefund.getRefundId()).getRefundId());
        assertEquals(ownerRefund.getRefundId(),
                refundService.getRefund(SELLER_ID, "SELLER", ownerRefund.getRefundId()).getRefundId());
        assertEquals(ownerRefund.getRefundId(),
                refundService.getRefund("ADMIN01", "ADMIN", ownerRefund.getRefundId()).getRefundId());

        BizException detailForbidden = assertThrows(BizException.class,
                () -> refundService.getRefund(OTHER_SELLER_ID, "SELLER", ownerRefund.getRefundId()));
        assertEquals(ErrorCode.FORBIDDEN, detailForbidden.getCode());

        BizException auditForbidden = assertThrows(BizException.class,
                () -> refundService.auditRefund(ownerRefund.getRefundId(), true,
                        OTHER_SELLER_ID, "SELLER", "越权"));
        assertEquals(ErrorCode.FORBIDDEN, auditForbidden.getCode());

        refundService.auditRefund(ownerRefund.getRefundId(), true, SELLER_ID, "SELLER", "同意");
        refundService.auditRefund(adminRefund.getRefundId(), true, "ADMIN01", "ADMIN", "管理员同意");

        RefundResponse adminDetail = refundService.getRefund(
                BUYER_ID, "BUYER", adminRefund.getRefundId());
        assertEquals("SUCCESS", adminDetail.getStatus().name());
        verify(orderClient, never()).markRefunded(ORDER_ID);
    }

    @Test
    void backfillsMissingSellerIdForLegacyRefund() {
        String legacyPaymentId = "PAYTEST0000000000004";
        String legacyRefundId = "REFTESTLEGACY0000000001";
        String legacyOrderId = "O50004";
        insertPaidPayment(legacyPaymentId, "P20261020000004", legacyOrderId,
                BUYER_ID, null, 5000L);
        insertRefund(legacyRefundId, legacyPaymentId, legacyOrderId,
                BUYER_ID, null, 1000L, "PENDING");
        when(orderClient.getOrder(legacyOrderId, BUYER_ID))
                .thenReturn(Result.success(order(legacyOrderId, SELLER_ID)));

        PageResult<RefundResponse> sellerPage = refundService.listRefunds(
                SELLER_ID, "SELLER", "PENDING", 1, 20);

        assertEquals(1, sellerPage.getTotal());
        assertEquals(legacyRefundId, sellerPage.getList().get(0).getRefundId());
        assertEquals(SELLER_ID, jdbcTemplate.queryForObject(
                "SELECT seller_id FROM refund WHERE refund_id = ?", String.class, legacyRefundId));
    }

    private void insertPaidPayment() {
        insertPaidPayment(PAYMENT_ID, PAY_NO, ORDER_ID, BUYER_ID, SELLER_ID, PAID_AMOUNT);
    }

    private void insertPaidPayment(String paymentId, String payNo, String orderId,
                                   String buyerId, String sellerId, long amount) {
        jdbcTemplate.update("""
                INSERT INTO payment (
                    payment_id, pay_no, order_id, buyer_id, seller_id, channel, amount,
                    refunded_amount, status, paid_at, created_by, updated_by, is_deleted
                ) VALUES (?, ?, ?, ?, ?, 'MOCK', ?, 0, 'SUCCESS', CURRENT_TIMESTAMP, ?, ?, 0)
                """, paymentId, payNo, orderId, buyerId, sellerId, amount, buyerId, buyerId);
    }

    private void insertRefund(String refundId, String paymentId, String orderId,
                              String buyerId, String sellerId, long amount, String status) {
        jdbcTemplate.update("""
                INSERT INTO refund (
                    refund_id, payment_id, order_id, buyer_id, seller_id, amount, reason,
                    status, created_by, updated_by, is_deleted
                ) VALUES (?, ?, ?, ?, ?, ?, '历史退款', ?, ?, ?, 0)
                """, refundId, paymentId, orderId, buyerId, sellerId, amount, status, buyerId, buyerId);
    }

    private OrderDTO order(String orderId, String sellerId) {
        OrderDTO order = new OrderDTO();
        order.setOrderId(orderId);
        order.setBuyerId(BUYER_ID);
        order.setSellerId(sellerId);
        order.setStatus(OrderStatus.PAID);
        order.setPayAmount(5000L);
        return order;
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
