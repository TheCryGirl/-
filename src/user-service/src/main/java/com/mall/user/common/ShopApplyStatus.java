package com.mall.user.common;

/**
 * 店铺入驻审核状态（#144）。
 *
 * <p>PENDING 待审核、REJECTED 已驳回时店铺不能发布商品；
 * APPROVED 表示资质通过，能否发布还取决于保证金是否已缴纳。
 */
public enum ShopApplyStatus {
    PENDING,
    APPROVED,
    REJECTED
}
