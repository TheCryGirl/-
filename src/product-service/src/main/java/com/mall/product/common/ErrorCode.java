package com.mall.product.common;

/**
 * 商品服务错误码，与《05-接口契约》1.4 节保持一致。
 */
public final class ErrorCode {

    public static final int SUCCESS = 0;
    public static final int PARAM_INVALID = 10001;
    public static final int UNAUTHORIZED = 10002;
    public static final int FORBIDDEN = 10003;
    public static final int SERVICE_UNAVAILABLE = 10005;
    public static final int SYSTEM_ERROR = 500;

    public static final int PRODUCT_NOT_FOUND = 30001;
    public static final int PRODUCT_OFF_SHELF = 30002;
    public static final int STOCK_INSUFFICIENT = 30003;
    public static final int RESERVATION_NOT_FOUND = 30004;
    public static final int SPEC_CONFLICT = 30005;
    public static final int SHOP_SUSPENDED = 30006;

    // #140 限时秒杀，从 30007 起，不与既有错误码撞号
    public static final int FLASH_SALE_NOT_FOUND = 30007;
    public static final int FLASH_SALE_SOLD_OUT = 30008;
    public static final int FLASH_SALE_LIMIT_EXCEEDED = 30009;

    private ErrorCode() {
    }
}
