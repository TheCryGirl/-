package com.mall.payment.common;

/**
 * 退款单状态。值必须与《05-接口契约》6.1 节一致。
 */
public enum RefundStatus {
    PENDING,
    REJECTED,
    SUCCESS,
    FAILED
}
