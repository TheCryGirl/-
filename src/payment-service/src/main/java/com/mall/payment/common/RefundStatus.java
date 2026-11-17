package com.mall.payment.common;

/**
 * 退款单状态。值必须与《05-接口契约》6.1 节一致。
 *
 * <p>#137 起真实渠道走 PENDING → PROCESSING → SUCCESS / FAILED；
 * MOCK 渠道即时到账，审核后直接 SUCCESS。FAILED 可人工重试回到 PROCESSING。
 */
public enum RefundStatus {
    PENDING,
    PROCESSING,
    REJECTED,
    SUCCESS,
    FAILED
}
