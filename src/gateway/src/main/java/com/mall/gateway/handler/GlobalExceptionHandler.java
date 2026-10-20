package com.mall.gateway.handler;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mall.gateway.common.ErrorCode;
import com.mall.gateway.common.Result;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebExceptionHandler;
import reactor.core.publisher.Mono;

import java.net.ConnectException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeoutException;

/**
 * 网关全局异常处理。
 *
 * <p>把下游不可用、超时这类异常转成统一响应结构，
 * 而不是让客户端收到原始的 500 或连接错误。
 */
@Order(-2)
@Component
public class GlobalExceptionHandler implements WebExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public Mono<Void> handle(ServerWebExchange exchange, Throwable ex) {
        ServerHttpResponse response = exchange.getResponse();
        if (response.isCommitted()) {
            return Mono.error(ex);
        }

        String path = exchange.getRequest().getURI().getPath();
        int code;
        String message;
        HttpStatus status;

        String msg = ex.getMessage() == null ? "" : ex.getMessage();

        if (msg.contains("Unable to find instance for")) {
            // 目标服务没注册到 Nacos，或服务名与路由配置不一致
            code = ErrorCode.SERVICE_UNAVAILABLE;
            message = "服务暂不可用，请稍后重试";
            status = HttpStatus.SERVICE_UNAVAILABLE;
            log.warn("服务未注册或服务名不匹配, path={}, detail={}", path, msg);
        } else if (ex instanceof ConnectException
                || ex.getCause() instanceof ConnectException
                || msg.contains("Connection refused")) {
            // 下游服务没启动或没注册到 Nacos
            code = ErrorCode.SERVICE_UNAVAILABLE;
            message = "服务暂不可用，请稍后重试";
            status = HttpStatus.SERVICE_UNAVAILABLE;
            log.warn("下游服务不可用, path={}", path);
        } else if (ex instanceof TimeoutException) {
            code = ErrorCode.SERVICE_UNAVAILABLE;
            message = "请求超时，请稍后重试";
            status = HttpStatus.GATEWAY_TIMEOUT;
            log.warn("下游服务超时, path={}", path);
        } else if (ex instanceof ResponseStatusException rse) {
            // 404 等：不把框架的英文原文返回给客户端
            code = ErrorCode.INTERNAL_ERROR;
            status = HttpStatus.valueOf(rse.getStatusCode().value());
            message = status == HttpStatus.NOT_FOUND ? "请求的接口不存在" : "请求失败";
            log.warn("请求异常, path={}, status={}", path, status.value());
        } else {
            code = ErrorCode.INTERNAL_ERROR;
            message = "系统繁忙，请稍后重试";
            status = HttpStatus.INTERNAL_SERVER_ERROR;
            log.error("网关未预期异常, path={}", path, ex);
        }

        response.setStatusCode(status);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);

        byte[] bytes;
        try {
            bytes = objectMapper.writeValueAsBytes(Result.fail(code, message));
        } catch (Exception e) {
            bytes = ("{\"code\":" + code + ",\"message\":\"" + message + "\"}")
                    .getBytes(StandardCharsets.UTF_8);
        }
        DataBuffer buffer = response.bufferFactory().wrap(bytes);
        return response.writeWith(Mono.just(buffer));
    }
}
