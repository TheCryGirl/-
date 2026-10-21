package com.mall.cart.common;

/**
 * 错误码常量，与《05-接口契约》1.4 节保持一致。
 */
public final class ErrorCode {

    public static final int SUCCESS = 0;
    public static final int PARAM_INVALID = 10001;
    public static final int UNAUTHORIZED = 10002;
    public static final int FORBIDDEN = 10003;
    public static final int SYSTEM_ERROR = 500;

    /** 商品不存在（product 段，购物车透传） */
    public static final int PRODUCT_NOT_FOUND = 30001;
    /** 商品已下架（product 段，购物车透传） */
    public static final int PRODUCT_OFF_SHELF = 30002;

    /** 购物车条目不存在 */
    public static final int CART_ITEM_NOT_FOUND = 40001;
    /** 数量超出可售库存 */
    public static final int QUANTITY_EXCEEDS_STOCK = 40002;

    private ErrorCode() {
    }
}
