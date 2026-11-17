package com.mall.payment.service;

import com.mall.payment.channel.ChannelNotification;
import com.mall.payment.channel.PaymentChannel;
import com.mall.payment.channel.PaymentChannelRegistry;
import com.mall.payment.common.ChannelType;
import com.mall.payment.common.ErrorCode;
import com.mall.payment.dto.request.PaymentCallbackRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * 渠道路由回调编排（#137）：POST /api/payments/callback/{channel}
 * 与 /api/payments/refund-callback/{channel} 共用。
 *
 * <p>顺序与旧回调一致：验签 → 解析 → （支付）去重/金额核对/状态更新，
 * 或（退款）退款回调幂等链。验签失败不改任何本地状态。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ChannelCallbackService {

    private final PaymentChannelRegistry channelRegistry;
    private final PaymentCallbackService paymentCallbackService;
    private final RefundCallbackService refundCallbackService;

    public ChannelCallbackOutcome processPayment(ChannelType channelType, Map<String, String> params) {
        PaymentChannel channel = channelRegistry.get(channelType);
        if (!channel.verifyCallback(params)) {
            log.error("渠道回调验签失败，拒绝处理, channel={}", channelType);
            return ChannelCallbackOutcome.fail(ErrorCode.SIGN_VERIFY_FAILED, "回调验签失败");
        }
        ChannelNotification notification = channel.parseCallback(params);
        if (notification.kind() == ChannelNotification.Kind.REFUND) {
            // 支付宝只用一个 notify_url，退款通知也会打到支付回调地址
            return refundCallbackService.processVerified(channelType, notification, params);
        }
        if (!notification.success()) {
            log.warn("暂不支持的回调状态, channel={}, tradeNo={}", channelType, notification.tradeNo());
            return ChannelCallbackOutcome.fail(ErrorCode.PARAM_INVALID, "暂不支持的回调状态");
        }
        PaymentCallbackRequest request = new PaymentCallbackRequest();
        request.setTradeNo(notification.tradeNo());
        request.setPayNo(notification.outTradeNo());
        request.setAmount(notification.amountFen());
        request.setStatus("SUCCESS");
        request.setPayTime(notification.payTime());
        request.setSign(params.get("sign"));
        CallbackResult result = paymentCallbackService.processVerified(request);
        return result.isSuccess()
                ? (result.isDuplicate() ? ChannelCallbackOutcome.duplicated() : ChannelCallbackOutcome.ok())
                : ChannelCallbackOutcome.fail(result.getCode(), result.getMessage());
    }

    public ChannelCallbackOutcome processRefund(ChannelType channelType, Map<String, String> params) {
        PaymentChannel channel = channelRegistry.get(channelType);
        if (!channel.verifyCallback(params)) {
            log.error("退款回调验签失败，拒绝处理, channel={}", channelType);
            return ChannelCallbackOutcome.fail(ErrorCode.SIGN_VERIFY_FAILED, "回调验签失败");
        }
        ChannelNotification notification = channel.parseCallback(params);
        if (notification.kind() != ChannelNotification.Kind.REFUND) {
            return ChannelCallbackOutcome.fail(ErrorCode.PARAM_INVALID, "不是退款通知");
        }
        return refundCallbackService.processVerified(channelType, notification, params);
    }
}