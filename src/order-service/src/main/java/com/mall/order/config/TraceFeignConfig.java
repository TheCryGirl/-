package com.mall.order.config;

import feign.RequestInterceptor;
import org.springframework.beans.factory.annotation.Value;
import org.slf4j.MDC;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Feign 调用透传链路追踪 ID。
 *
 * <p>服务之间的内部调用（Feign）不经过网关，若不显式传递 X-Trace-Id，
 * 下游服务就没有 TraceId，同一个业务流程的日志会断成几段。
 * 这里从当前线程的 MDC 取出 TraceId 写进请求头。
 */
@Configuration
public class TraceFeignConfig {

    @Value("${order.internal-token:${INTERNAL_TOKEN:}}")
    private String internalToken;

    @Bean
    public RequestInterceptor traceIdInterceptor() {
        return template -> {
            if (internalToken != null && !internalToken.isBlank()) {
                template.removeHeader("X-Internal-Token");
                template.header("X-Internal-Token", internalToken);
            }
            String traceId = MDC.get("traceId");
            if (traceId != null && !traceId.isBlank()) {
                template.header("X-Trace-Id", traceId);
            }
        };
    }
}