package com.mall.gateway.filter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * 链路追踪 ID 过滤器。
 *
 * <p>为每次进入网关的请求分配 TraceId：
 * <ul>
 *   <li>若调用方已带 {@code X-Trace-Id}（例如前端或压测脚本），沿用该值，便于端到端串联；</li>
 *   <li>否则生成一个新的 32 位十六进制 ID。</li>
 * </ul>
 * TraceId 会写入请求头传给下游服务，并回写到响应头，调用方可以直接拿到它去查日志。
 *
 * <p>本过滤器排在 {@link JwtAuthFilter} 之前，保证鉴权失败被拒绝的请求也有 TraceId。
 */
@Component
public class TraceIdFilter implements GlobalFilter, Ordered {

    public static final String HEADER_TRACE_ID = "X-Trace-Id";
    public static final String MDC_KEY = "traceId";

    private static final Logger log = LoggerFactory.getLogger(TraceIdFilter.class);

    /** 上游带来的 TraceId 长度上限，避免异常大的值写进日志。 */
    private static final int MAX_TRACE_ID_LENGTH = 64;

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String traceId = resolveTraceId(exchange.getRequest());

        ServerHttpRequest mutated = exchange.getRequest().mutate()
                .headers(headers -> headers.set(HEADER_TRACE_ID, traceId))
                .build();

        // 回写响应头，方便前端与排障时直接定位整条链路
        exchange.getResponse().getHeaders().set(HEADER_TRACE_ID, traceId);

        // Reactor 是异步模型，MDC 只在当前线程有效；这里仅用于网关自身日志，
        // 下游服务各自从请求头恢复自己的 MDC。
        MDC.put(MDC_KEY, traceId);
        try {
            log.debug("请求进入网关, traceId={}, path={}", traceId, exchange.getRequest().getURI().getPath());
        } finally {
            MDC.remove(MDC_KEY);
        }

        return chain.filter(exchange.mutate().request(mutated).build());
    }

    private String resolveTraceId(ServerHttpRequest request) {
        String existing = request.getHeaders().getFirst(HEADER_TRACE_ID);
        if (existing != null && !existing.isBlank() && existing.length() <= MAX_TRACE_ID_LENGTH) {
            return existing;
        }
        return UUID.randomUUID().toString().replace("-", "");
    }

    @Override
    public int getOrder() {
        // 比 JwtAuthFilter(-100) 更早执行
        return -200;
    }
}
