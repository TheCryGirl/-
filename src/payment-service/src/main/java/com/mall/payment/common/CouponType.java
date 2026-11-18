package com.mall.payment.common;

/**
 * 优惠券类型（#140）。FIXED 固定面额（分）；PERCENT 折扣比例，
 * value 为折扣百分比整数（85 表示 8.5 折），计算时统一走 Money.applyRate 只舍入一次。
 */
public enum CouponType {
    FIXED,
    PERCENT
}