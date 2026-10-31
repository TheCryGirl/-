package com.mall.payment.service;

import com.mall.payment.client.OrderClient;
import com.mall.payment.client.ProductClient;
import com.mall.payment.client.dto.ConfirmStockResponse;
import com.mall.payment.client.dto.OrderDTO;
import com.mall.payment.client.dto.StockConfirmRequest;
import com.mall.payment.common.OrderStatus;
import com.mall.payment.common.Result;
import com.mall.payment.entity.Payment;
import com.mall.payment.mapper.PaymentMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 支付成功后的对账任务。
 *
 * <p>支付回调里「通知订单已支付」和「确认扣减库存」是两次远程调用。任一失败时，
 * 支付单已是 SUCCESS 而订单仍停在待支付、库存仍处于预占态。回调本身不会重发，
 * 若不主动补偿，这笔单就永远对不上账。本任务周期性扫描
 * {@code status='SUCCESS' AND reconciled_at IS NULL} 的支付单并重放这两步。
 *
 * <p>两个下游操作都是幂等的：
 * <ul>
 *   <li>order-service 的 markPaid 走订单状态机，已是 PAID 直接返回；</li>
 *   <li>product-service 的 confirm 在预占已 CONFIRMED 时直接返回 true。</li>
 * </ul>
 * 因此重复执行安全，只在两边都成功时写入 reconciled_at。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PaymentReconcileJob {

    private final PaymentMapper paymentMapper;
    private final OrderClient orderClient;
    private final ProductClient productClient;

    /** 单次最多处理的支付单数量，避免一次扫太多把下游打满。 */
    @Value("${payment.reconcile.batch-size:50}")
    private int batchSize;

    /** 只对账最近这段时间内支付的单，更早的历史数据不自动处理。 */
    @Value("${payment.reconcile.lookback-hours:72}")
    private int lookbackHours;

    @Scheduled(fixedDelayString = "${payment.reconcile.interval-ms:60000}")
    public void reconcile() {
        LocalDateTime from = LocalDateTime.now().minusHours(Math.max(1, lookbackHours));
        List<Payment> pending;
        try {
            pending = paymentMapper.selectUnreconciledPaid(from, Math.max(1, batchSize));
        } catch (Exception e) {
            log.error("查询待对账支付单失败", e);
            return;
        }
        if (pending == null || pending.isEmpty()) {
            return;
        }
        log.info("开始支付后对账, 待处理={}", pending.size());
        int done = 0;
        for (Payment payment : pending) {
            if (reconcileOne(payment)) {
                done++;
            }
        }
        log.info("支付后对账完成, 待处理={}, 成功={}", pending.size(), done);
    }

    /**
     * @return 是否完成对账（两个下游都确认成功）
     */
    private boolean reconcileOne(Payment payment) {
        try {
            if (!reconcileOrder(payment)) {
                return false;
            }
            if (!reconcileStock(payment)) {
                return false;
            }
            paymentMapper.markReconciled(payment.getPaymentId(), LocalDateTime.now());
            log.warn("支付后对账补齐成功, paymentId={}, orderId={}",
                    payment.getPaymentId(), payment.getOrderId());
            return true;
        } catch (Exception e) {
            log.error("支付后对账异常, paymentId={}, orderId={}",
                    payment.getPaymentId(), payment.getOrderId(), e);
            return false;
        }
    }

    private boolean reconcileOrder(Payment payment) {
        OrderDTO order = queryOrder(payment);
        if (order == null) {
            return false;
        }
        if (order.getStatus() == OrderStatus.PAID) {
            return true;
        }
        if (order.getStatus() == OrderStatus.CANCELLED || order.getStatus() == OrderStatus.REFUNDED) {
            // 订单已被关闭而款项已到账，属于需要人工介入的异常单，交给回调侧同一套判断，
            // 这里不做自动退款，只记录并停止对账，避免与用户侧操作冲突。
            log.error("对账发现订单已关闭但支付成功，需人工处理, paymentId={}, orderId={}, orderStatus={}",
                    payment.getPaymentId(), payment.getOrderId(), order.getStatus());
            return false;
        }
        Result<Void> result = orderClient.markPaid(payment.getOrderId());
        if (result == null || result.getCode() != 0) {
            log.warn("对账补齐订单支付状态失败, orderId={}, code={}",
                    payment.getOrderId(), result == null ? null : result.getCode());
            return false;
        }
        return true;
    }

    private boolean reconcileStock(Payment payment) {
        Result<ConfirmStockResponse> result =
                productClient.confirmStock(new StockConfirmRequest(payment.getOrderId()));
        if (result == null || result.getCode() != 0) {
            log.warn("对账补齐库存扣减失败, orderId={}, code={}",
                    payment.getOrderId(), result == null ? null : result.getCode());
            return false;
        }
        ConfirmStockResponse data = result.getData();
        if (data == null || !data.confirmed()) {
            log.warn("对账补齐库存扣减未生效, orderId={}", payment.getOrderId());
            return false;
        }
        return true;
    }

    private OrderDTO queryOrder(Payment payment) {
        try {
            Result<OrderDTO> result = orderClient.getOrder(payment.getOrderId(), payment.getBuyerId());
            if (result == null || result.getCode() != 0 || result.getData() == null) {
                log.warn("对账查询订单失败, orderId={}, code={}",
                        payment.getOrderId(), result == null ? null : result.getCode());
                return null;
            }
            return result.getData();
        } catch (Exception e) {
            log.warn("对账查询订单异常, orderId={}", payment.getOrderId(), e);
            return null;
        }
    }
}
