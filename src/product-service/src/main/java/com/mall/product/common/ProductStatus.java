package com.mall.product.common;

public enum ProductStatus {
    PENDING,
    ON_SALE,
    OFF_SHELF,
    REJECTED,
    /** 店铺被管理员停用，商品随之不可见；恢复营业时由商品服务改回 ON_SALE */
    SUSPENDED
}
