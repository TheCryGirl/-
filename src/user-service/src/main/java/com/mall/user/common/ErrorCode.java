package com.mall.user.common;

/**
 * 错误码常量，与《05-接口契约》1.4 节保持一致。
 */
public final class ErrorCode {

    public static final int SUCCESS = 0;
    public static final int PARAM_INVALID = 10001;
    public static final int UNAUTHORIZED = 10002;
    public static final int FORBIDDEN = 10003;

    public static final int ACCOUNT_OR_PASSWORD_ERROR = 20001;
    public static final int ACCOUNT_EXISTS = 20002;
    public static final int NICKNAME_EXISTS = 20003;
    public static final int ACCOUNT_FROZEN = 20004;
    public static final int USER_NOT_FOUND = 20005;
    public static final int ADDRESS_NOT_FOUND = 20006;

    private ErrorCode() {
    }
}
