package com.mall.payment.common;

/**
 * 支付单状态。值必须与《05-接口契约》6.1 节一致。
 */
public enum PaymentStatus {
    PENDING,
    SUCCESS,
    FAILED,
    CLOSED,
    REFUNDED
}
