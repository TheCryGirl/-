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

    private ErrorCode() {
    }
}
