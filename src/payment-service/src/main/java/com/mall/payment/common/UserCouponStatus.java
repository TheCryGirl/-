package com.mall.payment.common;

/**
 * 用户持券状态（#140）。UNUSED 可用、USED 已使用（占用中或已核销）、
 * EXPIRED 已过期（列表按过期时间计算展示，不回写）。
 */
public enum UserCouponStatus {
    UNUSED,
    USED,
    EXPIRED
}