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

    // #140 促销工具（券/满减），从 60007 起，不与既有错误码撞号
    public static final int COUPON_NOT_FOUND = 60007;
    public static final int COUPON_OUT_OF_STOCK = 60008;
    public static final int COUPON_ALREADY_CLAIMED = 60009;
    public static final int COUPON_NOT_AVAILABLE = 60010;
    public static final int COUPON_ALREADY_USED = 60011;
    public static final int COUPON_MIN_NOT_MET = 60012;
    public static final int PROMOTION_NOT_FOUND = 60013;
    public static final int PROMOTION_CONFLICT = 60014;

    private ErrorCode() {
    }
}
