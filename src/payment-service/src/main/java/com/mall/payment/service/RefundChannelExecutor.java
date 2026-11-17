package com.mall.payment.service;

import com.mall.payment.channel.ChannelRefundResult;
import com.mall.payment.channel.PaymentChannel;
import com.mall.payment.channel.PaymentChannelRegistry;
import com.mall.payment.common.RefundStatus;
import com.mall.payment.entity.Payment;
import com.mall.payment.entity.Refund;
import com.mall.payment.mapper.PaymentMapper;
import com.mall.payment.mapper.RefundMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 渠道退款执行器（#137）：审核把退款单置 PROCESSING 后，
 * 在事务提交之外调用渠道退款，再按结果落终态。
 *
 * <p>渠道调用抛异常时保持 PROCESSING（渠道可能已受理），
 * 由退款回调、查单或人工重试收口；同一 refundId 是渠道幂等键。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RefundChannelExecutor {

    private final RefundMapper refundMapper;
    private final PaymentMapper paymentMapper;
    private final PaymentChannelRegistry channelRegistry;
    private final RefundTxService refundTxService;

    public void execute(String refundId, boolean notifyOrder) {
        Refund refund = refundMapper.selectById(refundId);
        if (refund == null || refund.getStatus() != RefundStatus.PROCESSING) {
            return;
        }
        Payment payment = paymentMapper.selectById(refund.getPaymentId());
        if (payment == null) {
            log.error("渠道退款找不到支付单, refundId={}, paymentId={}", refundId, refund.getPaymentId());
            return;
        }
        PaymentChannel channel = channelRegistry.get(payment.getChannel());
        ChannelRefundResult result;
        try {
            result = channel.refund(refund, payment);
        } catch (Exception e) {
            log.error("渠道退款调用异常，保持处理中, refundId={}", refundId, e);
            return;
        }
        switch (result.status()) {
            case SUCCESS -> refundTxService.completeSuccess(refundId, result.channelRefundNo(), notifyOrder);
            case FAILED -> refundTxService.markFailed(refundId, result.errorMessage());
            case PROCESSING -> log.info("渠道退款未返回终态，等待回调或人工重试, refundId={}", refundId);
        }
    }
}