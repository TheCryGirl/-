package com.mall.payment.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mall.payment.channel.ChannelNotification;
import com.mall.payment.common.ChannelType;
import com.mall.payment.common.ErrorCode;
import com.mall.payment.common.RefundStatus;
import com.mall.payment.entity.Refund;
import com.mall.payment.entity.RefundCallback;
import com.mall.payment.mapper.RefundCallbackMapper;
import com.mall.payment.mapper.RefundMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

/**
 * 退款异步通知处理（#137）。
 *
 * <p>验签由渠道实现在事务之前完成；这里只做幂等去重、金额核对与终态落库。
 * 去重靠 refund_callback.uk_channel_notify 唯一约束，不使用先查再插。
 * 金额不一致只记流水并拒绝，不修改退款单。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RefundCallbackService {

    private final RefundMapper refundMapper;
    private final RefundCallbackMapper refundCallbackMapper;
    private final RefundTxService refundTxService;
    private final ObjectMapper objectMapper;

    @Transactional(rollbackFor = Exception.class)
    public ChannelCallbackOutcome processVerified(ChannelType channelType,
                                                  ChannelNotification notification,
                                                  Map<String, String> params) {
        Refund refund = refundMapper.selectById(notification.outTradeNo());
        if (refund == null) {
            log.error("退款回调找不到退款单, refundId={}", notification.outTradeNo());
            return ChannelCallbackOutcome.fail(ErrorCode.PARAM_INVALID, "退款单不存在");
        }

        RefundCallback callback = new RefundCallback();
        callback.setChannel(channelType.name());
        callback.setNotifyId(notification.notifyId());
        callback.setRefundId(refund.getRefundId());
        callback.setRawPayload(toJson(params));
        callback.setSignVerified(true);
        callback.setAmount(notification.amountFen() == null ? 0L : notification.amountFen());
        callback.setProcessStatus("PROCESSING");
        try {
            refundCallbackMapper.insert(callback);
        } catch (DuplicateKeyException e) {
            log.warn("重复退款回调，忽略, channel={}, notifyId={}", channelType, notification.notifyId());
            return ChannelCallbackOutcome.duplicated();
        }

        if (notification.amountFen() == null || !refund.getAmount().equals(notification.amountFen())) {
            String error = "退款回调金额与退款单金额不一致";
            refundCallbackMapper.updateProcessStatus(callback.getCallbackId(), "AMOUNT_MISMATCH", error);
            log.error("REFUND_AMOUNT_MISMATCH, refundId={}, callbackAmount={}, refundAmount={}",
                    refund.getRefundId(), notification.amountFen(), refund.getAmount());
            return ChannelCallbackOutcome.fail(ErrorCode.AMOUNT_MISMATCH, error);
        }

        if (refund.getStatus() == RefundStatus.SUCCESS || refund.getStatus() == RefundStatus.REJECTED) {
            refundCallbackMapper.updateProcessStatus(callback.getCallbackId(), "DUPLICATE", "退款单已处理");
            return ChannelCallbackOutcome.duplicated();
        }
        if (refund.getStatus() != RefundStatus.PROCESSING) {
            // FAILED 由人工重试收口，迟到回调不直接改状态，避免与重试竞争
            refundCallbackMapper.updateProcessStatus(callback.getCallbackId(), "DUPLICATE",
                    "退款单状态为 " + refund.getStatus() + "，等待人工重试");
            return ChannelCallbackOutcome.duplicated();
        }

        if (notification.success()) {
            refundTxService.completeSuccess(refund.getRefundId(), notification.channelRefundNo(), true);
        } else {
            refundTxService.markFailed(refund.getRefundId(), "渠道退款失败通知");
        }
        refundCallbackMapper.updateProcessStatus(callback.getCallbackId(), "PROCESSED", null);
        return ChannelCallbackOutcome.ok();
    }

    private String toJson(Map<String, String> params) {
        try {
            return objectMapper.writeValueAsString(params);
        } catch (Exception e) {
            throw new IllegalStateException("退款回调参数序列化失败", e);
        }
    }
}