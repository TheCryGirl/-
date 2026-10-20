package com.mall.gateway.common;

/**
 * 错误码常量，与《05-接口契约》1.4 节保持一致。
 *
 * <p>网关只用到全局段（100xx），业务段的错误码由各服务自己定义。
 */
public final class ErrorCode {

    /** 成功 */
    public static final int SUCCESS = 0;

    /** 参数校验失败 */
    public static final int PARAM_INVALID = 10001;

    /** 未登录或令牌无效 */
    public static final int UNAUTHORIZED = 10002;

    /** 无权限执行该操作 */
    public static final int FORBIDDEN = 10003;

    /** 请求过于频繁 */
    public static final int TOO_MANY_REQUESTS = 10004;

    /** 服务暂不可用（下游挂了或未注册） */
    public static final int SERVICE_UNAVAILABLE = 10005;

    /** 网关内部错误 */
    public static final int INTERNAL_ERROR = 10006;

    private ErrorCode() {
    }
}
