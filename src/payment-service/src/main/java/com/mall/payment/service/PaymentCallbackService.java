package com.mall.payment.service;

import com.mall.payment.client.OrderClient;
import com.mall.payment.client.ProductClient;
import com.mall.payment.client.dto.OrderDTO;
import com.mall.payment.client.dto.StockConfirmRequest;
import com.mall.payment.common.ErrorCode;
import com.mall.payment.common.OrderStatus;
import com.mall.payment.common.Result;
import com.mall.payment.dto.request.PaymentCallbackRequest;
import com.mall.payment.dto.response.RefundResponse;
import com.mall.payment.entity.Payment;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

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
        OrderDTO order = queryOrder(payment);
        if (order == null) {
            log.error("支付成功但订单状态查询失败，跳过后续通知，等待人工补偿, paymentId={}, orderId={}",
                    payment.getPaymentId(), payment.getOrderId());
            return result;
        }
        if (order.getStatus() == OrderStatus.CANCELLED) {
            log.warn("订单已取消但收到支付成功回调，发起自动退款, paymentId={}, orderId={}",
                    payment.getPaymentId(), payment.getOrderId());
            try {
                RefundResponse refund = refundService.autoRefund(payment, "订单已取消，支付款自动退回");
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

        notifyOrderPaid(payment);
        confirmStock(payment);
        return result;
    }

    private OrderDTO queryOrder(Payment payment) {
        try {
            Result<OrderDTO> result = orderClient.getOrder(payment.getOrderId());
            if (result == null || result.getCode() != 0 || result.getData() == null) {
                log.error("查询订单失败, orderId={}, code={}",
                        payment.getOrderId(), result == null ? null : result.getCode());
                return null;
            }
            return result.getData();
        } catch (Exception e) {
            log.error("调用订单服务失败, orderId={}", payment.getOrderId(), e);
            return null;
        }
    }

    private void notifyOrderPaid(Payment payment) {
        try {
            Result<Void> result = orderClient.markPaid(payment.getOrderId());
            if (result == null || result.getCode() != 0) {
                log.error("通知订单支付成功失败, orderId={}, code={}",
                        payment.getOrderId(), result == null ? null : result.getCode());
            }
        } catch (Exception e) {
            log.error("通知订单支付成功异常, orderId={}", payment.getOrderId(), e);
        }
    }

    private void confirmStock(Payment payment) {
        try {
            Result<Void> result = productClient.confirmStock(new StockConfirmRequest(payment.getOrderId()));
            if (result == null || result.getCode() != 0) {
                log.error("确认扣减库存失败, orderId={}, code={}",
                        payment.getOrderId(), result == null ? null : result.getCode());
            }
        } catch (Exception e) {
            log.error("确认扣减库存异常, orderId={}", payment.getOrderId(), e);
        }
    }
}
