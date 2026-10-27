package com.mall.product.common;

public enum ReservationStatus {
    /** 已预占，尚未支付确认 */
    LOCKED,
    /** 支付成功，已转为真实扣减 */
    CONFIRMED,
    /** 超时或取消，预占已释放，库存已回补 */
    RELEASED,
    /** 退款成功，已扣减的库存已回补（BR-28） */
    REFUNDED
}
