package com.mall.payment.common;

/**
 * 回调流水的处理结果，用于对账与排查。
 */
public enum CallbackProcessStatus {
    PROCESSING,
    PROCESSED,
    DUPLICATE,
    AMOUNT_MISMATCH
}
