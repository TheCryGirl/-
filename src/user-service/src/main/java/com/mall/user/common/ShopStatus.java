package com.mall.user.common;

/**
 * 店铺营业状态。
 *
 * <p>ACTIVE 表示正常营业，商品对买家可见、可下单；
 * SUSPENDED 表示被管理员停用，商品不可见且不能下单。
 */
public enum ShopStatus {
    ACTIVE,
    SUSPENDED
}
