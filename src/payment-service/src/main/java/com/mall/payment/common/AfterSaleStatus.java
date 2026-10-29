package com.mall.payment.common;

/**
 * 售后单状态。状态流转全部使用条件 UPDATE，避免并发重复处理。
 *
 * <pre>
 * REFUND_ONLY:    PENDING -> (APPROVED) -> REFUNDED | REJECTED
 * RETURN_REFUND:  PENDING -> APPROVED -> BUYER_SHIPPED -> SELLER_RECEIVED -> REFUNDED | REJECTED
 * EXCHANGE:       PENDING -> APPROVED -> BUYER_SHIPPED -> COMPLETED | REJECTED
 * </pre>
 */
public enum AfterSaleStatus {
    PENDING,
    APPROVED,
    BUYER_SHIPPED,
    SELLER_RECEIVED,
    REFUNDED,
    COMPLETED,
    REJECTED
}
