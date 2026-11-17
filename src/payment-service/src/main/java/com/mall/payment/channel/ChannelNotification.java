package com.mall.payment.channel;

/**
 * 归一化渠道通知（支付成功 / 退款结果）。
 *
 * @param kind           PAYMENT 或 REFUND
 * @param notifyId       渠道通知唯一标识，用于退款回调幂等；MOCK 支付回调复用 tradeNo
 * @param tradeNo        渠道交易号
 * @param outTradeNo     商户单号：支付为 payNo，退款为 refundId（out_request_no）
 * @param amountFen      金额，单位分
 * @param success        渠道侧是否成功
 * @param channelRefundNo 退款成功时的渠道退款单号
 * @param payTime        渠道支付/退款时间，可为 null
 */
public record ChannelNotification(
        Kind kind,
        String notifyId,
        String tradeNo,
        String outTradeNo,
        Long amountFen,
        boolean success,
        String channelRefundNo,
        String payTime) {

    public enum Kind { PAYMENT, REFUND }
}