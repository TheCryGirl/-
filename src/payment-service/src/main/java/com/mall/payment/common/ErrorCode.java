package com.mall.payment.common;

/**
 * 错误码常量，与《05-接口契约》1.4 节保持一致。
 */
public final class ErrorCode {

    public static final int SUCCESS = 0;
    public static final int PARAM_INVALID = 10001;
    public static final int UNAUTHORIZED = 10002;
    public static final int FORBIDDEN = 10003;
    public static final int SERVICE_UNAVAILABLE = 10005;

    public static final int PAYMENT_NOT_FOUND = 60001;
    public static final int SIGN_VERIFY_FAILED = 60002;
    public static final int AMOUNT_MISMATCH = 60003;
    public static final int REFUND_EXCEEDS_PAID = 60004;
    public static final int AFTER_SALE_NOT_FOUND = 60005;
    public static final int AFTER_SALE_STATE_INVALID = 60006;

    private ErrorCode() {
    }
}
