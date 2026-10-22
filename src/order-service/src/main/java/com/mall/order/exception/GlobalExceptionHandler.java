package com.mall.order.exception;

import com.mall.order.common.BizException;
import com.mall.order.common.ErrorCode;
import com.mall.order.common.Result;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.stream.Collectors;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BizException.class)
    public Result<Void> handleBiz(BizException e) {
        return Result.fail(e.getCode(), e.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public Result<Void> handleValid(MethodArgumentNotValidException e) {
        String message = e.getBindingResult().getFieldErrors().stream()
                .map(FieldError::getDefaultMessage)
                .collect(Collectors.joining("; "));
        return Result.fail(ErrorCode.PARAM_INVALID, message.isBlank() ? "参数校验失败" : message);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public Result<Void> handleConstraint(ConstraintViolationException e) {
        return Result.fail(ErrorCode.PARAM_INVALID, e.getMessage());
    }

    @ExceptionHandler({HttpMessageNotReadableException.class, MissingRequestHeaderException.class})
    public Result<Void> handleBadRequest(Exception e) {
        return Result.fail(ErrorCode.PARAM_INVALID, "请求参数格式不正确");
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public Result<Void> handleIntegrity(DataIntegrityViolationException e) {
        log.warn("订单数据约束冲突", e);
        return Result.fail(ErrorCode.PARAM_INVALID, "数据已存在或不符合约束");
    }

    @ExceptionHandler(Exception.class)
    public Result<Void> handleOther(Exception e) {
        log.error("订单服务未预期异常", e);
        return Result.fail(ErrorCode.SYSTEM_ERROR, "系统繁忙，请稍后重试");
    }
}
