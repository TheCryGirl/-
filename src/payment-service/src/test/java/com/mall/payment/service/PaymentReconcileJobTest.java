package com.mall.payment.service;

import com.mall.payment.client.OrderClient;
import com.mall.payment.client.ProductClient;
import com.mall.payment.client.dto.ConfirmStockResponse;
import com.mall.payment.client.dto.OrderDTO;
import com.mall.payment.client.dto.StockConfirmRequest;
import com.mall.payment.common.ErrorCode;
import com.mall.payment.common.OrderStatus;
import com.mall.payment.common.Result;
import com.mall.payment.mapper.PaymentMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 支付成功后的对账补偿（PaymentReconcileJob）。
 *
 * <p>覆盖「支付单已是 SUCCESS，但通知订单/确认库存失败」的场景：
 * 对账任务必须能把这些单补齐，并在补齐后不再重复处理。
 *
 * <p>另覆盖「回调晚到、订单已经走过支付环节」的场景：订单处于 SHIPPED/COMPLETED 时
 * markPaid 在订单状态机里是非法的（PENDING_PAYMENT 才能迁到 PAID），对账若先调 markPaid，
 * 订单侧直接抛 ORDER_STATE_INVALID，这条支付单就会永远停在 reconciled_at 为空、库存也
 * 确认不了。因此这里要求：已支付过的状态不调 markPaid，但库存确认和写 reconciled_at 照做。
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

    /**
     * Spy 而不是 @MockBean：需要在不清空 ApplicationContext（@MockBean 新增一条就会让整批用例
     * 重新启动上下文）的前提下，让 selectUnreconciledPaid 抛异常，验证「查库失败只跳过本轮」。
     */
    @SpyBean
    private PaymentMapper paymentMapper;

    @MockBean
    private OrderClient orderClient;

    @MockBean
    private ProductClient productClient;

    @BeforeEach
    void setUp() {
        jdbcTemplate.update("DELETE FROM refund_notification_task");
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

    // ------------------------------------------------------------------
    // 回调晚到：订单已经发货/完成，支付单仍是 SUCCESS 且 reconciled_at 为空
    // ------------------------------------------------------------------

    @Test
    void shippedOrderIsNotMarkedPaidAgainButStockAndReconcileStillComplete() {
        when(orderClient.getOrder(ORDER_ID, BUYER_ID))
                .thenReturn(Result.success(order(OrderStatus.SHIPPED)));
        // 状态机里 SHIPPED 不能迁回 PAID，真调了订单服务这里会抛业务异常
        when(orderClient.markPaid(ORDER_ID))
                .thenThrow(new IllegalStateException("订单状态不能从 SHIPPED 迁移到 PAID"));
        when(productClient.confirmStock(any(StockConfirmRequest.class)))
                .thenReturn(Result.success(new ConfirmStockResponse(true)));

        reconcileJob.reconcile();

        // 订单已经走过 PAID，再调 markPaid 只会在订单状态机里抛异常并把这条单卡死
        verify(orderClient, never()).markPaid(ORDER_ID);
        // 库存确认和对账时间不能跟着一起跳过
        verify(productClient, times(1)).confirmStock(any(StockConfirmRequest.class));
        assertNotNull(reconciledAt(), "订单已发货时库存确认成功后仍应写入 reconciled_at");
    }

    @Test
    void completedOrderIsNotMarkedPaidAgainButStockAndReconcileStillComplete() {
        when(orderClient.getOrder(ORDER_ID, BUYER_ID))
                .thenReturn(Result.success(order(OrderStatus.COMPLETED)));
        when(orderClient.markPaid(ORDER_ID))
                .thenThrow(new IllegalStateException("订单状态不能从 COMPLETED 迁移到 PAID"));
        when(productClient.confirmStock(any(StockConfirmRequest.class)))
                .thenReturn(Result.success(new ConfirmStockResponse(true)));

        reconcileJob.reconcile();

        verify(orderClient, never()).markPaid(ORDER_ID);
        verify(productClient, times(1)).confirmStock(any(StockConfirmRequest.class));
        assertNotNull(reconciledAt(), "订单已完成时库存确认成功后仍应写入 reconciled_at");
    }

    @Test
    void shippedOrderKeepsReconciledNullWhenStockConfirmFails() {
        when(orderClient.getOrder(ORDER_ID, BUYER_ID))
                .thenReturn(Result.success(order(OrderStatus.SHIPPED)));
        when(productClient.confirmStock(any(StockConfirmRequest.class)))
                .thenReturn(Result.success(new ConfirmStockResponse(false)));

        reconcileJob.reconcile();

        // 库存没确认成功就不能算对账完成，留 NULL 等下一轮重试
        assertNull(reconciledAt(), "库存确认失败时 reconciled_at 必须保持 NULL");
        verify(orderClient, never()).markPaid(ORDER_ID);

        // 下一轮库存恢复正常后应能补齐，说明上面只是留待重试而不是永久放弃
        when(productClient.confirmStock(any(StockConfirmRequest.class)))
                .thenReturn(Result.success(new ConfirmStockResponse(true)));
        reconcileJob.reconcile();

        verify(productClient, times(2)).confirmStock(any(StockConfirmRequest.class));
        assertNotNull(reconciledAt(), "重试成功后才写 reconciled_at");
        verify(orderClient, never()).markPaid(ORDER_ID);
    }

    /**
     * 订单已取消：属于回调侧同一套「人工处理」分支，不能自动当成功，
     * 也不能再去确认扣减库存（此时预占多半已释放，confirm 必然失败）。
     */
    @Test
    void cancelledOrderIsLeftToManualHandlingWithoutConfirmingStock() {
        when(orderClient.getOrder(ORDER_ID, BUYER_ID))
                .thenReturn(Result.success(order(OrderStatus.CANCELLED)));
        when(productClient.confirmStock(any(StockConfirmRequest.class)))
                .thenReturn(Result.success(new ConfirmStockResponse(true)));

        reconcileJob.reconcile();

        verify(orderClient, never()).markPaid(ORDER_ID);
        verify(productClient, never()).confirmStock(any(StockConfirmRequest.class));
        assertNull(reconciledAt(), "订单已取消的单不能自动对账成功");
    }

    @Test
    void refundedOrderIsLeftToManualHandlingWithoutConfirmingStock() {
        when(orderClient.getOrder(ORDER_ID, BUYER_ID))
                .thenReturn(Result.success(order(OrderStatus.REFUNDED)));
        when(productClient.confirmStock(any(StockConfirmRequest.class)))
                .thenReturn(Result.success(new ConfirmStockResponse(true)));

        reconcileJob.reconcile();

        verify(orderClient, never()).markPaid(ORDER_ID);
        verify(productClient, never()).confirmStock(any(StockConfirmRequest.class));
        assertNull(reconciledAt(), "订单已退款的单不能自动对账成功");
    }

    // ------------------------------------------------------------------
    // 扫描本身出错：不能让异常穿透到调度线程，否则整个定时任务会被停掉
    // ------------------------------------------------------------------

    @Test
    void scanFailureDoesNotPropagateAndKeepsPaymentPending() {
        when(paymentMapper.selectUnreconciledPaid(any(), anyInt()))
                .thenThrow(new IllegalStateException("数据库连接不可用"));

        // 不让异常穿透：调度线程里抛 RuntimeException 会导致后续所有周期都不再执行
        reconcileJob.reconcile();

        verify(orderClient, never()).markPaid(any());
        verify(productClient, never()).confirmStock(any(StockConfirmRequest.class));
        assertNull(reconciledAt(), "扫库失败时本轮什么都不做，reconciled_at 保持 NULL");
    }

    /**
     * 查不到订单时不能往下走：既不能改订单状态，也不能碰库存，reconciled_at 留 NULL。
     */
    @Test
    void orderLookupFailureLeavesPaymentUntouched() {
        when(orderClient.getOrder(ORDER_ID, BUYER_ID))
                .thenReturn(Result.fail(ErrorCode.SERVICE_UNAVAILABLE, "订单服务不可用"));

        reconcileJob.reconcile();

        verify(orderClient, never()).markPaid(ORDER_ID);
        verify(productClient, never()).confirmStock(any(StockConfirmRequest.class));
        assertNull(reconciledAt());
    }

    @Test
    void schedulerSwitchControlsOnlyAutomaticRuns() {
        when(orderClient.getOrder(ORDER_ID, BUYER_ID)).thenReturn(Result.success(order(OrderStatus.PAID)));
        when(productClient.confirmStock(any(StockConfirmRequest.class)))
                .thenReturn(Result.success(new ConfirmStockResponse(true)));
        reconcileJob.scheduledReconcile();
        verify(productClient, never()).confirmStock(any(StockConfirmRequest.class));
        org.springframework.test.util.ReflectionTestUtils.setField(reconcileJob, "scheduledEnabled", true);
        try {
            reconcileJob.scheduledReconcile();
            verify(productClient, times(1)).confirmStock(any(StockConfirmRequest.class));
            assertNotNull(reconciledAt());
        } finally {
            org.springframework.test.util.ReflectionTestUtils.setField(reconcileJob, "scheduledEnabled", false);
        }
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
