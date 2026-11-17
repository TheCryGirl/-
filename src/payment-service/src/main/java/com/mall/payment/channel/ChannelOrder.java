package com.mall.payment.channel;

/**
 * 渠道下单结果。
 *
 * @param payUrl         收银台地址（MOCK 为 mock:// 协议，支付宝沙箱为网关跳转地址）
 * @param channelTradeNo 渠道交易号；下单即返回的渠道才有值，否则为 null
 */
public record ChannelOrder(String payUrl, String channelTradeNo) {
}