package com.mall.gateway.filter;

import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 链路追踪过滤器：请求头透传与生成规则。
 *
 * <p>直接实例化过滤器测试，不加载 Spring 上下文——本类只依赖过滤器自身的逻辑。
 */
class TraceIdFilterTest {

    private final TraceIdFilter traceIdFilter = new TraceIdFilter();

    @Test
    void generatesTraceIdWhenAbsent() {
        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/api/products").build());
        AtomicReference<ServerWebExchange> forwarded = new AtomicReference<>();

        traceIdFilter.filter(exchange, chainCapturing(forwarded)).block();

        String upstream = forwarded.get().getRequest().getHeaders().getFirst(TraceIdFilter.HEADER_TRACE_ID);
        assertNotNull(upstream, "网关应为无 TraceId 的请求生成一个");
        assertEquals(32, upstream.length(), "生成的 TraceId 应为 32 位十六进制（UUID 去掉横线）");
        assertEquals(upstream, exchange.getResponse().getHeaders().getFirst(TraceIdFilter.HEADER_TRACE_ID),
                "TraceId 应回写到响应头");
    }

    @Test
    void reusesIncomingTraceId() {
        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/api/products")
                .header(TraceIdFilter.HEADER_TRACE_ID, "trace-from-caller")
                .build());
        AtomicReference<ServerWebExchange> forwarded = new AtomicReference<>();

        traceIdFilter.filter(exchange, chainCapturing(forwarded)).block();

        assertEquals("trace-from-caller",
                forwarded.get().getRequest().getHeaders().getFirst(TraceIdFilter.HEADER_TRACE_ID),
                "调用方已带 TraceId 时应沿用，便于端到端串联");
    }

    @Test
    void replacesOverlongTraceId() {
        StringBuilder tooLong = new StringBuilder();
        for (int i = 0; i < 200; i++) {
            tooLong.append('a');
        }
        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/api/products")
                .header(TraceIdFilter.HEADER_TRACE_ID, tooLong.toString())
                .build());
        AtomicReference<ServerWebExchange> forwarded = new AtomicReference<>();

        traceIdFilter.filter(exchange, chainCapturing(forwarded)).block();

        String upstream = forwarded.get().getRequest().getHeaders().getFirst(TraceIdFilter.HEADER_TRACE_ID);
        assertNotNull(upstream);
        assertTrue(upstream.length() <= 64, "超长 TraceId 应被替换，避免污染日志");
    }

    @Test
    void runsBeforeJwtFilter() {
        assertTrue(traceIdFilter.getOrder() < -100,
                "TraceId 过滤器应早于 JwtAuthFilter(-100)，保证被拒绝的请求也有 TraceId");
    }

    private static GatewayFilterChain chainCapturing(AtomicReference<ServerWebExchange> holder) {
        return exchange -> {
            holder.set(exchange);
            return Mono.empty();
        };
    }
}
