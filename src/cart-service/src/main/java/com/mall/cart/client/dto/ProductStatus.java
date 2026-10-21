package com.mall.cart.client.dto;

/**
 * 商品状态，取值与 product-service 的 ProductStatus 一致。
 */
public enum ProductStatus {
    PENDING,
    ON_SALE,
    OFF_SHELF,
    REJECTED
}
