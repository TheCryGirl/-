package com.mall.payment.service;

import com.mall.payment.client.OrderClient;
import com.mall.payment.client.ProductClient;
import com.mall.payment.client.dto.ConfirmStockResponse;
import com.mall.payment.client.dto.MainOrderDTO;
import com.mall.payment.client.dto.OrderDTO;
import com.mall.payment.client.dto.StockConfirmRequest;
import com.mall.payment.common.ErrorCode;
import com.mall.payment.common.OrderStatus;
import com.mall.payment.common.Result;
import com.mall.payment.dto.request.PaymentCallbackRequest;
import com.mall.payment.dto.response.RefundResponse;
import com.mall.payment.entity.Payment;
import com.mall.payment.mapper.PaymentMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

/**
 * 支付回调编排。
 *
 * <p>严格顺序：验签 → 唯一键去重 → 金额核对 → 更新支付单 → 通知订单与库存。
 * 远程调用放在本地事务提交之后，避免事务占用数据库连接，也避免远程失败回滚本地状态。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentCallbackService {

    private final SignatureService signatureService;
    private final CallbackTxService callbackTxService;
    private final OrderClient orderClient;
    private final ProductClient productClient;
    private final RefundService refundService;
    private final PaymentMapper paymentMapper;

    public CallbackResult process(PaymentCallbackRequest request) {
        if (!signatureService.verify(request)) {
            log.error("回调验签失败，拒绝处理, tradeNo={}, payNo={}",
                    request.getTradeNo(), request.getPayNo());
            return CallbackResult.fail(ErrorCode.SIGN_VERIFY_FAILED, "回调验签失败", null);
        }
        if (!"SUCCESS".equalsIgnoreCase(request.getStatus())) {
            log.warn("暂不支持的回调状态, tradeNo={}, status={}",
                    request.getTradeNo(), request.getStatus());
            return CallbackResult.fail(ErrorCode.PARAM_INVALID, "暂不支持的回调状态", null);
        }

        CallbackResult result = callbackTxService.handle(request);
        if (!result.isSuccess() || !result.isNewlyPaid()) {
            return result;
        }

        Payment payment = result.getPayment();
        // 跨店合并结算（#135）：支付单 order_id 为主订单ID，逐个子订单通知与确认库存
        if (payment.getOrderId() != null && payment.getOrderId().startsWith("M")) {
            processMainOrderPayment(payment);
            return result;
        }
        OrderDTO order = queryOrder(payment);
        if (order == null) {
            log.error("支付成功但订单状态查询失败，跳过后续通知，等待人工补偿, paymentId={}, orderId={}",
                    payment.getPaymentId(), payment.getOrderId());
            return result;
        }
        if (order.getStatus() == OrderStatus.CANCELLED
                || order.getStatus() == OrderStatus.REFUNDED) {
            String reason = order.getStatus() == OrderStatus.CANCELLED
                    ? "订单已取消，支付款自动退回"
                    : "订单已退款，重复支付款自动退回";
            log.warn("订单已关闭但收到支付成功回调，发起自动退款, paymentId={}, orderId={}, orderStatus={}",
                    payment.getPaymentId(), payment.getOrderId(), order.getStatus());
            try {
                RefundResponse refund = refundService.autoRefund(payment, reason);
                if (refund != null) {
                    log.warn("自动退款完成, paymentId={}, refundId={}, amount={}",
                            payment.getPaymentId(), refund.getRefundId(), refund.getAmount());
                }
            } catch (Exception e) {
                log.error("自动退款失败，需人工处理, paymentId={}, orderId={}",
                        payment.getPaymentId(), payment.getOrderId(), e);
            }
            return result;
        }

        boolean orderOk = notifyOrderPaid(payment);
        boolean stockOk = confirmStock(payment);
        if (orderOk && stockOk) {
            markReconciled(payment);
        }
        return result;
    }

    private OrderDTO queryOrder(Payment payment) {
        try {
            Result<OrderDTO> result = orderClient.getOrder(payment.getOrderId(), payment.getBuyerId());
            if (result == null || result.getCode() != 0 || result.getData() == null) {
                log.error("查询订单失败, orderId={}, code={}",
                        payment.getOrderId(), result == null ? null : result.getCode());
                return null;
            }
            return result.getData();
        } catch (Exception e) {
            log.error("调用订单服务失败, orderId={}, buyerId={}",
                    payment.getOrderId(), payment.getBuyerId(), e);
            return null;
        }
    }

    /**
     * 跨店合并结算的回调后编排（#135）。
     *
     * <p>主订单已取消：整笔自动退款（与单店 BR-18 一致）。
     * 否则逐个子订单通知已支付并确认库存；任一失败留给 PaymentReconcileJob 重试。
     * 主订单状态由 order-service 在全部子订单 PAID 时自动置 PAID，这里不直接改主订单。
     */
    private void processMainOrderPayment(Payment payment) {
        MainOrderDTO mainOrder = queryMainOrder(payment);
        if (mainOrder == null) {
            log.error("支付成功但主订单查询失败，跳过后续通知，等待对账任务重试, paymentId={}, mainOrderId={}",
                    payment.getPaymentId(), payment.getOrderId());
            return;
        }
        if (mainOrder.getStatus() == OrderStatus.CANCELLED) {
            log.warn("主订单已取消但收到支付成功回调，发起自动退款, paymentId={}, mainOrderId={}",
                    payment.getPaymentId(), payment.getOrderId());
            autoRefundQuietly(payment, "订单已取消，支付款自动退回");
            return;
        }
        if (mainOrder.getStatus() == OrderStatus.REFUNDED) {
            log.warn("主订单已退款但收到支付成功回调，发起自动退款, paymentId={}, mainOrderId={}",
                    payment.getPaymentId(), payment.getOrderId());
            autoRefundQuietly(payment, "订单已退款，重复支付款自动退回");
            return;
        }
        if (mainOrder.getSubOrders() == null || mainOrder.getSubOrders().isEmpty()) {
            log.error("主订单缺少子订单，等待对账任务重试, paymentId={}, mainOrderId={}",
                    payment.getPaymentId(), payment.getOrderId());
            return;
        }
        boolean allOk = true;
        for (OrderDTO subOrder : mainOrder.getSubOrders()) {
            if (subOrder.getStatus() == OrderStatus.CANCELLED) {
                // 已取消的子订单不接受支付通知；其预占库存已在取消时释放
                continue;
            }
            if (!notifySubOrderPaid(payment, subOrder.getOrderId())) {
                allOk = false;
            }
            if (!confirmSubOrderStock(payment, subOrder.getOrderId())) {
                allOk = false;
            }
        }
        if (allOk) {
            markReconciled(payment);
        }
    }

    private MainOrderDTO queryMainOrder(Payment payment) {
        try {
            Result<MainOrderDTO> result = orderClient.getMainOrder(payment.getOrderId(), payment.getBuyerId());
            if (result == null || result.getCode() != 0 || result.getData() == null) {
                log.error("查询主订单失败, mainOrderId={}, code={}",
                        payment.getOrderId(), result == null ? null : result.getCode());
                return null;
            }
            return result.getData();
        } catch (Exception e) {
            log.error("调用订单服务失败, mainOrderId={}, buyerId={}",
                    payment.getOrderId(), payment.getBuyerId(), e);
            return null;
        }
    }

    /** 主订单关闭后的整笔自动退款；失败只记日志，需人工处理（与单店 BR-18 一致）。 */
    private void autoRefundQuietly(Payment payment, String reason) {
        try {
            RefundResponse refund = refundService.autoRefund(payment, reason);
            if (refund != null) {
                log.warn("自动退款完成, paymentId={}, refundId={}, amount={}",
                        payment.getPaymentId(), refund.getRefundId(), refund.getAmount());
            }
        } catch (Exception e) {
            log.error("自动退款失败，需人工处理, paymentId={}, mainOrderId={}",
                    payment.getPaymentId(), payment.getOrderId(), e);
        }
    }

    private boolean notifySubOrderPaid(Payment payment, String orderId) {
        try {
            Result<Void> result = orderClient.markPaid(orderId);
            if (result == null || result.getCode() != 0) {
                log.error("通知子订单支付成功失败, orderId={}, code={}，等待对账任务重试",
                        orderId, result == null ? null : result.getCode());
                return false;
            }
            return true;
        } catch (Exception e) {
            log.error("通知子订单支付成功异常, orderId={}，等待对账任务重试", orderId, e);
            return false;
        }
    }

    private boolean confirmSubOrderStock(Payment payment, String orderId) {
        try {
            Result<ConfirmStockResponse> result =
                    productClient.confirmStock(new StockConfirmRequest(orderId));
            if (result == null || result.getCode() != 0) {
                log.error("确认扣减库存失败, orderId={}, code={}，等待对账任务重试",
                        orderId, result == null ? null : result.getCode());
                return false;
            }
            ConfirmStockResponse data = result.getData();
            if (data == null || !data.confirmed()) {
                log.error("库存确认未生效, orderId={}, data={}，等待对账任务重试", orderId, data);
                return false;
            }
            return true;
        } catch (Exception e) {
            log.error("确认扣减库存异常, orderId={}，等待对账任务重试", orderId, e);
            return false;
        }
    }

    /**
     * 标记支付单已完成支付后对账。失败时留给 PaymentReconcileJob 重试，不阻断回调返回。
     */
    private void markReconciled(Payment payment) {        try {
            paymentMapper.markReconciled(payment.getPaymentId(), LocalDateTime.now());
        } catch (Exception e) {
            log.warn("标记对账完成失败，将由对账任务重试, paymentId={}", payment.getPaymentId(), e);
        }
    }

    private boolean notifyOrderPaid(Payment payment) {
        try {
            Result<Void> result = orderClient.markPaid(payment.getOrderId());
            if (result == null || result.getCode() != 0) {
                log.error("通知订单支付成功失败, orderId={}, code={}，等待对账任务重试",
                        payment.getOrderId(), result == null ? null : result.getCode());
                return false;
            }
            return true;
        } catch (Exception e) {
            log.error("通知订单支付成功异常, orderId={}，等待对账任务重试", payment.getOrderId(), e);
            return false;
        }
    }

    private boolean confirmStock(Payment payment) {
        try {
            Result<ConfirmStockResponse> result =
                    productClient.confirmStock(new StockConfirmRequest(payment.getOrderId()));
            if (result == null || result.getCode() != 0) {
                log.error("确认扣减库存失败, orderId={}, code={}，等待对账任务重试",
                        payment.getOrderId(), result == null ? null : result.getCode());
                return false;
            }
            ConfirmStockResponse data = result.getData();
            if (data == null || !data.confirmed()) {
                log.error("库存确认未生效, orderId={}, data={}，等待对账任务重试", payment.getOrderId(), data);
                return false;
            }
            return true;
        } catch (Exception e) {
            log.error("确认扣减库存异常, orderId={}，等待对账任务重试", payment.getOrderId(), e);
            return false;
        }
    }
}
