package com.mall.payment.service;

import com.mall.payment.client.OrderClient;
import com.mall.payment.client.ProductClient;
import com.mall.payment.client.dto.OrderDTO;
import com.mall.payment.client.dto.StockConfirmRequest;
import com.mall.payment.common.AfterSaleStatus;
import com.mall.payment.common.AfterSaleType;
import com.mall.payment.common.BizException;
import com.mall.payment.common.ErrorCode;
import com.mall.payment.common.OrderStatus;
import com.mall.payment.common.PageResult;
import com.mall.payment.common.Result;
import com.mall.payment.dto.request.CreateAfterSaleRequest;
import com.mall.payment.dto.response.AfterSaleResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SpringBootTest
class AfterSaleServiceIntegrationTest {

    private static final String ORDER_ID = "O70001";
    private static final String PAYMENT_ID = "PAYTEST0000000000007";
    private static final String PAY_NO = "P20261029000007";
    private static final String BUYER_ID = "U10001";
    private static final String SELLER_ID = "S10001";
    private static final String OTHER_SELLER_ID = "S20002";

    @Autowired
    private AfterSaleService afterSaleService;

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
        stubOrder(OrderStatus.PAID);
        when(orderClient.markRefunded(ORDER_ID)).thenReturn(Result.success(null));
        when(productClient.refundRestock(any(StockConfirmRequest.class))).thenReturn(Result.success(null));
    }

    @Test
    void refundOnlyFlowIsSellerIsolatedAndPartialDoesNotNotifyOrder() {
        AfterSaleResponse ticket = create("refund-only-1", AfterSaleType.REFUND_ONLY, 5000L);
        assertEquals(AfterSaleStatus.PENDING, ticket.getStatus());
        assertEquals(SELLER_ID, ticket.getSellerId());
        assertEquals(5000L, ticket.getAmount());

        assertEquals(1, afterSaleService.list(BUYER_ID, "BUYER", null, null, null, 1, 20).getTotal());
        assertEquals(1, afterSaleService.list(SELLER_ID, "SELLER", null, null, null, 1, 20).getTotal());
        assertEquals(0, afterSaleService.list(OTHER_SELLER_ID, "SELLER", null, null, null, 1, 20).getTotal());
        assertEquals(1, afterSaleService.list("ADMIN01", "ADMIN", null, null, null, 1, 20).getTotal());
        assertEquals(ticket.getTicketId(),
                afterSaleService.detail(SELLER_ID, "SELLER", ticket.getTicketId()).getTicketId());

        BizException detailForbidden = assertThrows(BizException.class,
                () -> afterSaleService.detail(OTHER_SELLER_ID, "SELLER", ticket.getTicketId()));
        assertEquals(ErrorCode.FORBIDDEN, detailForbidden.getCode());

        BizException auditForbidden = assertThrows(BizException.class,
                () -> afterSaleService.audit(ticket.getTicketId(), OTHER_SELLER_ID,
                        "SELLER", true, "越权"));
        assertEquals(ErrorCode.FORBIDDEN, auditForbidden.getCode());

        AfterSaleResponse refunded = afterSaleService.audit(
                ticket.getTicketId(), SELLER_ID, "SELLER", true, "同意");
        assertEquals(AfterSaleStatus.REFUNDED, refunded.getStatus());
        assertNotNull(refunded.getRefundId());
        assertEquals("SUCCESS", refundStatus());
        assertEquals(5000L, refundedAmount());
        assertEquals("SUCCESS", paymentStatus());
        verify(orderClient, never()).markRefunded(ORDER_ID);
        verify(productClient, never()).refundRestock(any(StockConfirmRequest.class));

        AfterSaleResponse repeated = afterSaleService.audit(
                ticket.getTicketId(), SELLER_ID, "SELLER", true, "重复同意");
        assertEquals(AfterSaleStatus.REFUNDED, repeated.getStatus());
        assertEquals(1, count("refund"));
    }

    @Test
    void fullRefundOnlyNotifiesOrderAndRestocks() {
        AfterSaleResponse ticket = create("refund-full-1", AfterSaleType.REFUND_ONLY, 11800L);

        AfterSaleResponse refunded = afterSaleService.audit(
                ticket.getTicketId(), SELLER_ID, "SELLER", true, "同意");

        assertEquals(AfterSaleStatus.REFUNDED, refunded.getStatus());
        assertEquals("REFUNDED", paymentStatus());
        assertEquals(11800L, refundedAmount());
        verify(orderClient, times(1)).markRefunded(ORDER_ID);
        verify(productClient, times(1)).refundRestock(any(StockConfirmRequest.class));
        assertEquals(1, count("refund"));
    }

    @Test
    void returnRefundFlowRequiresShipmentAndSellerReceipt() {
        AfterSaleResponse ticket = create("return-1", AfterSaleType.RETURN_REFUND, 5000L);
        AfterSaleResponse approved = afterSaleService.audit(
                ticket.getTicketId(), SELLER_ID, "SELLER", true, "同意退货");
        assertEquals(AfterSaleStatus.APPROVED, approved.getStatus());

        BizException wrongBuyer = assertThrows(BizException.class,
                () -> afterSaleService.shipReturn(ticket.getTicketId(), "U20002", "SF-RETURN-1"));
        assertEquals(ErrorCode.FORBIDDEN, wrongBuyer.getCode());

        AfterSaleResponse shipped = afterSaleService.shipReturn(
                ticket.getTicketId(), BUYER_ID, "SF-RETURN-1");
        assertEquals(AfterSaleStatus.BUYER_SHIPPED, shipped.getStatus());
        assertEquals("SF-RETURN-1", shipped.getReturnTrackingNo());

        AfterSaleResponse sameShipment = afterSaleService.shipReturn(
                ticket.getTicketId(), BUYER_ID, "SF-RETURN-1");
        assertEquals(AfterSaleStatus.BUYER_SHIPPED, sameShipment.getStatus());
        BizException changeTracking = assertThrows(BizException.class,
                () -> afterSaleService.shipReturn(ticket.getTicketId(), BUYER_ID, "SF-RETURN-2"));
        assertEquals(ErrorCode.AFTER_SALE_STATE_INVALID, changeTracking.getCode());

        BizException wrongSeller = assertThrows(BizException.class,
                () -> afterSaleService.receiveReturn(ticket.getTicketId(),
                        OTHER_SELLER_ID, "SELLER", "确认收货"));
        assertEquals(ErrorCode.FORBIDDEN, wrongSeller.getCode());

        AfterSaleResponse refunded = afterSaleService.receiveReturn(
                ticket.getTicketId(), SELLER_ID, "SELLER", "确认收货");
        assertEquals(AfterSaleStatus.REFUNDED, refunded.getStatus());
        assertEquals(1, count("refund"));
        assertEquals(5000L, refundedAmount());
        assertEquals("SUCCESS", paymentStatus());
        verify(orderClient, never()).markRefunded(ORDER_ID);

        AfterSaleResponse repeated = afterSaleService.receiveReturn(
                ticket.getTicketId(), SELLER_ID, "SELLER", "重复确认");
        assertEquals(AfterSaleStatus.REFUNDED, repeated.getStatus());
        assertEquals(1, count("refund"));
    }

    @Test
    void exchangeFlowSupportsBuyerReturnAndSellerReship() {
        AfterSaleResponse ticket = create("exchange-1", AfterSaleType.EXCHANGE, null);
        assertEquals(0L, ticket.getAmount());

        AfterSaleResponse approved = afterSaleService.audit(
                ticket.getTicketId(), SELLER_ID, "SELLER", true, "同意换货");
        assertEquals(AfterSaleStatus.APPROVED, approved.getStatus());

        AfterSaleResponse shipped = afterSaleService.shipReturn(
                ticket.getTicketId(), BUYER_ID, "SF-EX-RETURN-1");
        assertEquals(AfterSaleStatus.BUYER_SHIPPED, shipped.getStatus());

        AfterSaleResponse completed = afterSaleService.reshipExchange(
                ticket.getTicketId(), SELLER_ID, "SELLER", "SF-EX-NEW-1", "已重新发货");
        assertEquals(AfterSaleStatus.COMPLETED, completed.getStatus());
        assertEquals("SF-EX-NEW-1", completed.getExchangeTrackingNo());
        assertEquals(0, count("refund"));
        verify(orderClient, never()).markRefunded(ORDER_ID);
        verify(productClient, never()).refundRestock(any(StockConfirmRequest.class));

        AfterSaleResponse repeated = afterSaleService.reshipExchange(
                ticket.getTicketId(), SELLER_ID, "SELLER", "SF-EX-NEW-1", "重复发货");
        assertEquals(AfterSaleStatus.COMPLETED, repeated.getStatus());
        BizException changeTracking = assertThrows(BizException.class,
                () -> afterSaleService.reshipExchange(
                        ticket.getTicketId(), SELLER_ID, "SELLER", "SF-EX-NEW-2", "换单号"));
        assertEquals(ErrorCode.AFTER_SALE_STATE_INVALID, changeTracking.getCode());
    }

    @Test
    void rejectAndActiveTicketRules() {
        AfterSaleResponse first = create("reject-1", AfterSaleType.REFUND_ONLY, 1000L);
        AfterSaleResponse rejected = afterSaleService.audit(
                first.getTicketId(), SELLER_ID, "SELLER", false, "不符合条件");
        assertEquals(AfterSaleStatus.REJECTED, rejected.getStatus());
        assertEquals(0, count("refund"));

        AfterSaleResponse second = create("active-1", AfterSaleType.REFUND_ONLY, 1000L);
        assertEquals(AfterSaleStatus.PENDING, second.getStatus());

        BizException activeExists = assertThrows(BizException.class,
                () -> create("active-2", AfterSaleType.REFUND_ONLY, 1000L));
        assertEquals(ErrorCode.PARAM_INVALID, activeExists.getCode());
        assertEquals(2, count("after_sale_ticket"));

        AfterSaleResponse sameKey = create("active-1", AfterSaleType.REFUND_ONLY, 1000L);
        assertEquals(second.getTicketId(), sameKey.getTicketId());

        AfterSaleResponse repeatedReject = afterSaleService.audit(
                first.getTicketId(), SELLER_ID, "SELLER", false, "重复驳回");
        assertEquals(AfterSaleStatus.REJECTED, repeatedReject.getStatus());
    }

    @Test
    void validationAndCancelledOrderAreRejected() {
        BizException overAmount = assertThrows(BizException.class,
                () -> create("amount-too-high", AfterSaleType.REFUND_ONLY, 11801L));
        assertEquals(ErrorCode.REFUND_EXCEEDS_PAID, overAmount.getCode());

        BizException missingAmount = assertThrows(BizException.class,
                () -> create("amount-missing", AfterSaleType.REFUND_ONLY, null));
        assertEquals(ErrorCode.PARAM_INVALID, missingAmount.getCode());

        stubOrder(OrderStatus.CANCELLED);
        BizException cancelled = assertThrows(BizException.class,
                () -> create("cancelled-order", AfterSaleType.REFUND_ONLY, 1000L));
        assertEquals(ErrorCode.PARAM_INVALID, cancelled.getCode());

        BizException invalidType = assertThrows(BizException.class,
                () -> afterSaleService.list(BUYER_ID, "BUYER", null, "UNKNOWN", null, 1, 20));
        assertEquals(ErrorCode.PARAM_INVALID, invalidType.getCode());
    }

    private AfterSaleResponse create(String key, AfterSaleType type, Long amount) {
        CreateAfterSaleRequest request = new CreateAfterSaleRequest();
        request.setOrderId(ORDER_ID);
        request.setType(type);
        request.setAmount(amount);
        request.setReason("集成测试售后");
        return afterSaleService.create(BUYER_ID, key, request);
    }

    private void stubOrder(OrderStatus status) {
        OrderDTO order = new OrderDTO();
        order.setOrderId(ORDER_ID);
        order.setBuyerId(BUYER_ID);
        order.setSellerId(SELLER_ID);
        order.setStatus(status);
        order.setPayAmount(11800L);
        when(orderClient.getOrder(ORDER_ID, BUYER_ID)).thenReturn(Result.success(order));
    }

    private void insertPaidPayment() {
        jdbcTemplate.update("""
                INSERT INTO payment (
                    payment_id, pay_no, order_id, buyer_id, seller_id, channel, amount,
                    refunded_amount, status, paid_at, created_by, updated_by, is_deleted
                ) VALUES (?, ?, ?, ?, ?, 'MOCK', 11800, 0, 'SUCCESS', CURRENT_TIMESTAMP, ?, ?, 0)
                """, PAYMENT_ID, PAY_NO, ORDER_ID, BUYER_ID, SELLER_ID, BUYER_ID, BUYER_ID);
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

    private String refundStatus() {
        return jdbcTemplate.queryForObject(
                "SELECT status FROM refund WHERE payment_id = ?", String.class, PAYMENT_ID);
    }

    private int count(String table) {
        Integer count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM " + table, Integer.class);
        return count == null ? 0 : count;
    }
}
