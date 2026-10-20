package com.mall.gateway.filter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mall.gateway.common.ErrorCode;
import com.mall.gateway.common.Result;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * JWT 鉴权过滤器。
 *
 * <p>对应《05-接口契约》1.5 节：网关统一校验令牌，校验通过后把 userId 与 role
 * 放进请求头 {@code X-User-Id} / {@code X-User-Role} 传给下游服务，
 * 下游服务不再重复解析 JWT。
 *
 * <p>白名单路径直接放行，不做校验。
 */
@Component
public class JwtAuthFilter implements GlobalFilter, Ordered {

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(JwtAuthFilter.class);

    /** 下游读取用户身份用的请求头 */
    public static final String HEADER_USER_ID = "X-User-Id";
    public static final String HEADER_USER_ROLE = "X-User-Role";

    private static final AntPathMatcher MATCHER = new AntPathMatcher();

    /**
     * 白名单：无需登录即可访问。
     * 与契约 1.5 节保持一致，改这里要同步改契约。
     */
    private static final List<String> WHITE_LIST = List.of(
            "/api/users/register",
            "/api/users/login",
            "/api/products/**",
            "/api/categories/**",
            "/images/**",
            "/actuator/**"
    );

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${jwt.secret}")
    private String jwtSecret;

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        String path = request.getURI().getPath();

        // 1. 白名单直接放行
        if (isWhiteListed(path)) {
            return chain.filter(exchange);
        }

        // 2. 取令牌
        String authHeader = request.getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return reject(exchange, ErrorCode.UNAUTHORIZED, "未登录或令牌缺失");
        }
        String token = authHeader.substring(7);

        // 3. 解析令牌
        Claims claims;
        try {
            SecretKey key = Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8));
            claims = Jwts.parser()
                    .verifyWith(key)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
        } catch (JwtException | IllegalArgumentException e) {
            log.debug("令牌校验失败, path={}, reason={}", path, e.getMessage());
            return reject(exchange, ErrorCode.UNAUTHORIZED, "登录已过期，请重新登录");
        }

        // 4. 把身份放进请求头，覆盖客户端可能伪造的同名头
        String userId = claims.get("userId", String.class);
        String role = claims.get("role", String.class);
        if (userId == null || userId.isBlank()) {
            return reject(exchange, ErrorCode.UNAUTHORIZED, "令牌内容不完整");
        }

        ServerHttpRequest mutated = request.mutate()
                .headers(headers -> {
                    headers.remove(HEADER_USER_ID);
                    headers.remove(HEADER_USER_ROLE);
                    headers.set(HEADER_USER_ID, userId);
                    if (role != null) {
                        headers.set(HEADER_USER_ROLE, role);
                    }
                })
                .build();

        return chain.filter(exchange.mutate().request(mutated).build());
    }

    private boolean isWhiteListed(String path) {
        return WHITE_LIST.stream().anyMatch(pattern -> MATCHER.match(pattern, path));
    }

    /** 返回统一结构的 401 响应 */
    private Mono<Void> reject(ServerWebExchange exchange, int code, String message) {
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(HttpStatus.UNAUTHORIZED);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);

        byte[] bytes;
        try {
            bytes = objectMapper.writeValueAsBytes(Result.fail(code, message));
        } catch (Exception e) {
            bytes = ("{\"code\":" + code + ",\"message\":\"" + message + "\"}").getBytes(StandardCharsets.UTF_8);
        }
        DataBuffer buffer = response.bufferFactory().wrap(bytes);
        return response.writeWith(Mono.just(buffer));
    }

    @Override
    public int getOrder() {
        // 数值越小越先执行，放在路由之前
        return -100;
    }
}
