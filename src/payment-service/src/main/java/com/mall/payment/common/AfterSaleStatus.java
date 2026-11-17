package com.mall.payment.common;

/**
 * 售后单状态。状态流转全部使用条件 UPDATE，避免并发重复处理。
 *
 * <pre>
 * REFUND_ONLY:    PENDING -> (APPROVED) -> REFUNDED | REJECTED
 * RETURN_REFUND:  PENDING -> APPROVED -> BUYER_SHIPPED -> SELLER_RECEIVED -> REFUNDED | REJECTED
 * EXCHANGE:       PENDING -> APPROVED -> BUYER_SHIPPED -> COMPLETED | REJECTED
 *
 * #143 增量：
 *   PENDING -> CANCELLED（买家撤销，终态）
 *   REJECTED -> ARBITRATING（买家申请平台仲裁，仲裁期间锁定，卖家不可审核）
 *   ARBITRATING -> REFUNDED（管理员裁定支持退款）| REJECTED（管理员裁定驳回）
 * </pre>
 */
public enum AfterSaleStatus {
    PENDING,
    APPROVED,
    BUYER_SHIPPED,
    SELLER_RECEIVED,
    REFUNDED,
    COMPLETED,
    REJECTED,
    CANCELLED,
    ARBITRATING
}
