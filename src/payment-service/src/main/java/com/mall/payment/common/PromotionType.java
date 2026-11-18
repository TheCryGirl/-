package com.mall.payment.common;

/**
 * 满减/满折活动类型（#140）。FULL_REDUCE 满 threshold 减 value 分；
 * FULL_DISCOUNT 满 threshold 打 value 折（value 为百分比整数，85 = 8.5 折）。
 */
public enum PromotionType {
    FULL_REDUCE,
    FULL_DISCOUNT
}