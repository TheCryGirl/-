package com.mall.cart.client.dto;

/**
 * 商品状态，取值与 product-service 的 ProductStatus 一致。
 */
public enum ProductStatus {
    PENDING,
    ON_SALE,
    OFF_SHELF,
    REJECTED,
    /** 店铺被停用，商品暂时不可购买 */
    SUSPENDED
}
