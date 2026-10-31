package com.mall.payment.service;

import com.mall.payment.client.OrderClient;
import com.mall.payment.client.ProductClient;
import com.mall.payment.client.dto.ConfirmStockResponse;
import com.mall.payment.client.dto.OrderDTO;
import com.mall.payment.client.dto.StockConfirmRequest;
import com.mall.payment.common.ErrorCode;
import com.mall.payment.common.OrderStatus;
import com.mall.payment.common.Result;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 支付成功后的对账补偿（PaymentReconcileJob）。
 *
 * <p>覆盖「支付单已是 SUCCESS，但通知订单/确认库存失败」的场景：
 * 对账任务必须能把这些单补齐，并在补齐后不再重复处理。
 */
@SpringBootTest
class PaymentReconcileJobTest {

    private static final String PAYMENT_ID = "PAYRECON000000000001";
    private static final String PAY_NO = "P20261031000001";
    private static final String ORDER_ID = "O60001";
    private static final String BUYER_ID = "U20001";
    private static final long AMOUNT = 9900L;

    @Autowired
    private PaymentReconcileJob reconcileJob;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @MockBean
    private OrderClient orderClient;

    @MockBean
    private ProductClient productClient;

    @BeforeEach
    void setUp() {
        jdbcTemplate.update("DELETE FROM after_sale_ticket");
        jdbcTemplate.update("DELETE FROM refund");
        jdbcTemplate.update("DELETE FROM payment_callback");
        jdbcTemplate.update("DELETE FROM payment");
        insertPaidPayment();
    }

    @Test
    void reconcilesOrderAndStockWhenBothFailedEarlier() {
        when(orderClient.getOrder(ORDER_ID, BUYER_ID))
                .thenReturn(Result.success(order(OrderStatus.PENDING_PAYMENT)));
        when(orderClient.markPaid(ORDER_ID)).thenReturn(Result.success(null));
        when(productClient.confirmStock(any(StockConfirmRequest.class)))
                .thenReturn(Result.success(new ConfirmStockResponse(true)));

        reconcileJob.reconcile();

        verify(orderClient, times(1)).markPaid(ORDER_ID);
        verify(productClient, times(1)).confirmStock(any(StockConfirmRequest.class));
        assertNotNull(reconciledAt(), "对账成功后应写入 reconciled_at");
    }

    @Test
    void keepsPendingWhenOrderNotifyStillFails() {
        when(orderClient.getOrder(ORDER_ID, BUYER_ID))
                .thenReturn(Result.success(order(OrderStatus.PENDING_PAYMENT)));
        when(orderClient.markPaid(ORDER_ID)).thenReturn(Result.fail(ErrorCode.SERVICE_UNAVAILABLE, "订单服务不可用"));
        when(productClient.confirmStock(any(StockConfirmRequest.class)))
                .thenReturn(Result.success(new ConfirmStockResponse(true)));

        reconcileJob.reconcile();

        assertNull(reconciledAt(), "补齐失败时不应写入 reconciled_at，留待下次重试");
        verify(productClient, never()).confirmStock(any(StockConfirmRequest.class));
    }

    @Test
    void keepsPendingWhenStockConfirmFails() {
        when(orderClient.getOrder(ORDER_ID, BUYER_ID))
                .thenReturn(Result.success(order(OrderStatus.PAID)));
        when(productClient.confirmStock(any(StockConfirmRequest.class)))
                .thenReturn(Result.success(new ConfirmStockResponse(false)));

        reconcileJob.reconcile();

        assertNull(reconciledAt());
        // 订单已是 PAID 时不应再调 markPaid
        verify(orderClient, never()).markPaid(ORDER_ID);
    }

    @Test
    void skipsAlreadyReconciledPayments() {
        when(orderClient.getOrder(ORDER_ID, BUYER_ID))
                .thenReturn(Result.success(order(OrderStatus.PAID)));
        when(productClient.confirmStock(any(StockConfirmRequest.class)))
                .thenReturn(Result.success(new ConfirmStockResponse(true)));
        reconcileJob.reconcile();

        // 第二轮不应再处理
        reconcileJob.reconcile();

        verify(productClient, times(1)).confirmStock(any(StockConfirmRequest.class));
    }

    private void insertPaidPayment() {
        jdbcTemplate.update("""
                INSERT INTO payment (
                    payment_id, pay_no, order_id, buyer_id, channel, amount,
                    refunded_amount, status, paid_at, created_by, updated_by, is_deleted
                ) VALUES (?, ?, ?, ?, 'MOCK', ?, 0, 'SUCCESS', CURRENT_TIMESTAMP, ?, ?, 0)
                """, PAYMENT_ID, PAY_NO, ORDER_ID, BUYER_ID, AMOUNT, BUYER_ID, BUYER_ID);
    }

    private OrderDTO order(OrderStatus status) {
        OrderDTO order = new OrderDTO();
        order.setOrderId(ORDER_ID);
        order.setOrderNo("2026103100000012345");
        order.setStatus(status);
        order.setBuyerId(BUYER_ID);
        order.setPayAmount(AMOUNT);
        return order;
    }

    private Object reconciledAt() {
        return jdbcTemplate.queryForObject(
                "SELECT reconciled_at FROM payment WHERE payment_id = ?", Object.class, PAYMENT_ID);
    }
}
