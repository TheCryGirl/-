package com.mall.payment.common;

/**
 * 订单服务对外暴露的状态值，只读使用，支付服务不修改订单状态。
 */
public enum OrderStatus {
    PENDING_PAYMENT,
    PAID,
    SHIPPED,
    COMPLETED,
    CANCELLED,
    REFUNDED
}
