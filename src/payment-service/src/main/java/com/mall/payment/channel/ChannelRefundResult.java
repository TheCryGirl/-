package com.mall.payment.channel;

/**
 * 渠道退款结果。
 *
 * @param status          SUCCESS / FAILED / PROCESSING（渠道未给出终态，等待回调或人工重试）
 * @param channelRefundNo 渠道退款单号，成功时必有
 * @param errorMessage    失败原因（不含凭据）
 */
public record ChannelRefundResult(Status status, String channelRefundNo, String errorMessage) {

    public enum Status { SUCCESS, FAILED, PROCESSING }

    public static ChannelRefundResult success(String channelRefundNo) {
        return new ChannelRefundResult(Status.SUCCESS, channelRefundNo, null);
    }

    public static ChannelRefundResult failed(String errorMessage) {
        return new ChannelRefundResult(Status.FAILED, null, errorMessage);
    }

    public static ChannelRefundResult processing() {
        return new ChannelRefundResult(Status.PROCESSING, null, null);
    }
}