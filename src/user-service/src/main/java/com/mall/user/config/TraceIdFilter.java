package com.mall.user.config;

import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;

/**
 * 从网关透传的 {@code X-Trace-Id} 请求头恢复 TraceId 到日志上下文。
 *
 * <p>配合 logback 的 {@code %X{traceId}} 输出，同一次请求在网关与各服务里的日志
 * 可以用同一个 ID 串起来。请求头缺失时（例如直连服务、内部调用）不写入，
 * 日志格式里对应位置留空，不影响原有输出。
 */
@Component
public class TraceIdFilter extends OncePerRequestFilter {

    public static final String HEADER_TRACE_ID = "X-Trace-Id";
    public static final String MDC_KEY = "traceId";

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String traceId = request.getHeader(HEADER_TRACE_ID);
        boolean owned = traceId != null && !traceId.isBlank();
        if (owned) {
            MDC.put(MDC_KEY, traceId);
        }
        try {
            filterChain.doFilter(request, response);
        } finally {
            if (owned) {
                MDC.remove(MDC_KEY);
            }
        }
    }
}
