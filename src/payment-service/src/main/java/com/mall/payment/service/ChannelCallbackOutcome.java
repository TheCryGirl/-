package com.mall.payment.service;

/**
 * 渠道路由回调的处理结果（#137）。
 * 支付宝渠道按渠道规定返回纯文本 success/failure，MOCK 渠道返回统一 Result。
 */
public record ChannelCallbackOutcome(boolean success, boolean duplicate, int code, String message) {

    public static ChannelCallbackOutcome ok() {
        return new ChannelCallbackOutcome(true, false, 0, "success");
    }

    public static ChannelCallbackOutcome duplicated() {
        return new ChannelCallbackOutcome(true, true, 0, "success");
    }

    public static ChannelCallbackOutcome fail(int code, String message) {
        return new ChannelCallbackOutcome(false, false, code, message);
    }
}