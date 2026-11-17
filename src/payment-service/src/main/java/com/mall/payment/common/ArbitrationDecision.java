package com.mall.payment.common;

/**
 * 平台仲裁裁定结果（#143）。
 * REFUND：支持买家退款，售后单 ARBITRATING -> REFUNDED 并联动退款单；
 * REJECT：驳回申诉，售后单 ARBITRATING -> REJECTED。
 */
public enum ArbitrationDecision {
    REFUND,
    REJECT
}