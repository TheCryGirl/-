package com.mall.payment.common;

/**
 * 支付渠道类型（存储值）。渠道行为见 {@code com.mall.payment.channel.PaymentChannel}。
 */
public enum ChannelType {
    ALIPAY,
    WECHAT,
    MOCK
}
