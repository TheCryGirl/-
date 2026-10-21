package com.mall.product.common;

import lombok.Getter;

/**
 * 业务异常。错误码统一从 {@link ErrorCode} 取，由全局异常处理器转成 Result。
 */
@Getter
public class BizException extends RuntimeException {

    private final int code;

    public BizException(int code, String message) {
        super(message);
        this.code = code;
    }

    public BizException(int code, String message, Throwable cause) {
        super(message, cause);
        this.code = code;
    }
}
