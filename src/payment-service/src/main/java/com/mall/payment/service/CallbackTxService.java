package com.mall.payment.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mall.payment.common.CallbackProcessStatus;
import com.mall.payment.common.ErrorCode;
import com.mall.payment.common.PaymentStatus;
import com.mall.payment.dto.request.PaymentCallbackRequest;
import com.mall.payment.entity.Payment;
import com.mall.payment.entity.PaymentCallback;
import com.mall.payment.mapper.PaymentCallbackMapper;
import com.mall.payment.mapper.PaymentMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * 回调的本地事务处理：写流水、金额核对、更新支付单。
 *
 * <p>验签在事务之前完成；验签失败不会进入这里，因此不会写任何状态。
 * 去重靠 payment_callback.uk_trade_no 唯一约束，不使用先查再插。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CallbackTxService {

    private final PaymentMapper paymentMapper;
    private final PaymentCallbackMapper callbackMapper;
    private final ObjectMapper objectMapper;

    @Transactional(rollbackFor = Exception.class)
    public CallbackResult handle(PaymentCallbackRequest request) {
        Payment payment = paymentMapper.selectByPayNo(request.getPayNo());
        if (payment == null) {
            return CallbackResult.fail(ErrorCode.PAYMENT_NOT_FOUND, "支付单不存在", null);
        }

        PaymentCallback callback = new PaymentCallback();
        callback.setTradeNo(request.getTradeNo());
        callback.setPaymentId(payment.getPaymentId());
        callback.setRawPayload(toJson(request));
        callback.setSignVerified(true);
        callback.setAmount(request.getAmount());
        callback.setProcessStatus(CallbackProcessStatus.PROCESSING);

        try {
            callbackMapper.insert(callback);
        } catch (DuplicateKeyException e) {
            log.warn("重复回调，忽略, tradeNo={}, paymentId={}",
                    request.getTradeNo(), payment.getPaymentId());
            return CallbackResult.duplicate(payment);
        }

        if (!payment.getAmount().equals(request.getAmount())) {
            String error = "回调金额与支付单金额不一致";
            callbackMapper.updateProcessStatus(callback.getCallbackId(),
                    CallbackProcessStatus.AMOUNT_MISMATCH, error);
            log.error("PAYMENT_AMOUNT_MISMATCH, tradeNo={}, payNo={}, callbackAmount={}, paymentAmount={}",
                    request.getTradeNo(), request.getPayNo(), request.getAmount(), payment.getAmount());
            return CallbackResult.fail(ErrorCode.AMOUNT_MISMATCH, error, payment);
        }

        if (payment.getStatus() == PaymentStatus.SUCCESS
                || payment.getStatus() == PaymentStatus.REFUNDED) {
            callbackMapper.updateProcessStatus(callback.getCallbackId(),
                    CallbackProcessStatus.DUPLICATE, "支付单已处理");
            log.info("支付单已处理，回调按幂等成功返回, tradeNo={}, paymentId={}",
                    request.getTradeNo(), payment.getPaymentId());
            return CallbackResult.duplicate(payment);
        }

        LocalDateTime paidAt = LocalDateTime.now();
        int updated = paymentMapper.markSuccess(payment.getPaymentId(), paidAt);
        if (updated == 0) {
            Payment latest = paymentMapper.selectById(payment.getPaymentId());
            callbackMapper.updateProcessStatus(callback.getCallbackId(),
                    CallbackProcessStatus.DUPLICATE, "支付单已被并发处理");
            log.info("支付单已被并发处理，回调按幂等成功返回, tradeNo={}, paymentId={}",
                    request.getTradeNo(), payment.getPaymentId());
            return CallbackResult.duplicate(latest);
        }

        callbackMapper.updateProcessStatus(callback.getCallbackId(),
                CallbackProcessStatus.PROCESSED, null);
        payment.setStatus(PaymentStatus.SUCCESS);
        payment.setPaidAt(paidAt);
        log.info("支付成功, paymentId={}, orderId={}, amount={}",
                payment.getPaymentId(), payment.getOrderId(), payment.getAmount());
        return CallbackResult.newlyPaid(payment);
    }

    private String toJson(PaymentCallbackRequest request) {
        try {
            return objectMapper.writeValueAsString(request);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("回调报文序列化失败", e);
        }
    }
}
