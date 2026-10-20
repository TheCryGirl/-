package com.mall.payment.common;

import lombok.Data;

/**
 * 统一响应结构，与《05-接口契约》1.3 节保持一致。
 */
@Data
public class Result<T> {

    private int code;
    private String message;
    private T data;
    private long timestamp;

    public Result() {
    }

    public Result(int code, String message, T data, long timestamp) {
        this.code = code;
        this.message = message;
        this.data = data;
        this.timestamp = timestamp;
    }

    public static <T> Result<T> success(T data) {
        return new Result<>(ErrorCode.SUCCESS, "success", data, System.currentTimeMillis());
    }

    public static <T> Result<T> fail(int code, String message) {
        return new Result<>(code, message, null, System.currentTimeMillis());
    }
}
