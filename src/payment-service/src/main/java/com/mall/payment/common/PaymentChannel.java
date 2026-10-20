package com.mall.payment.common;

/**
 * 支付渠道。开发期只实现 MOCK，另两个值预留给后续接入。
 */
public enum PaymentChannel {
    ALIPAY,
    WECHAT,
    MOCK
}
