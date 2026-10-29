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
import org.springframework.http.HttpMethod;
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
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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
     * 仅 POST 可匿名访问的白名单。
     * 支付回调由签名保证来源可信，不能要求登录。
     */
    private static final List<String> PUBLIC_POST = List.of(
            "/api/users/register",
            "/api/users/login",
            "/api/payments/callback"
    );

    /**
     * 仅 GET 可匿名访问的白名单。
     * 商品写操作（发布/修改/上下架/审核）与 /api/products/mine 必须登录，
     * 因此不能笼统放行 /api/products/**。
     */
    private static final List<String> PUBLIC_GET = List.of(
            "/api/categories/**",
            "/api/evaluations/products/**",
            "/images/**",
            "/avatars/**",
            "/actuator/**"
    );

    private static final Pattern PRODUCT_DETAIL_PATH = Pattern.compile("^/api/products/([^/]+)$");

    /** /api/products/{x} 中属于功能路径而非商品 ID 的保留字 */
    private static final Set<String> PRODUCT_RESERVED_SEGMENTS = Set.of("mine", "skus", "images", "batch", "internal");

    private final ObjectMapper objectMapper = new ObjectMapper();

    /** 签名密钥，启动时从环境变量 JWT_SECRET 注入并校验，不提供硬编码默认值 */
    private final SecretKey key;

    public JwtAuthFilter(@Value("${jwt.secret}") String jwtSecret) {
        if (jwtSecret == null || jwtSecret.length() < 32) {
            throw new IllegalArgumentException("JWT_SECRET 长度至少 32 位，请通过环境变量配置后再启动网关");
        }
        this.key = Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8));
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        String path = request.getURI().getPath();

        // 1. 白名单直接放行（按方法与路径精确判定）
        //    但若请求带了令牌，仍然解析并注入身份——否则 /api/products/{id} 这类
        //    公开接口下，卖家/管理员访问自己待审核的商品时，下游拿不到 userId，
        //    会被当成匿名访客而拒绝。令牌无效时按匿名处理，不阻断。
        if (isWhiteListed(request.getMethod(), path)) {
            String whitelistAuth = request.getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
            if (whitelistAuth != null && whitelistAuth.startsWith("Bearer ")) {
                Claims claims = tryParse(whitelistAuth.substring(7), path);
                if (claims != null) {
                    return chain.filter(exchange.mutate()
                            .request(withIdentity(request, claims))
                            .build());
                }
            }
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
        if (userId == null || userId.isBlank()) {
            return reject(exchange, ErrorCode.UNAUTHORIZED, "令牌内容不完整");
        }

        return chain.filter(exchange.mutate().request(withIdentity(request, claims)).build());
    }

    /** 解析令牌；失败返回 null（白名单场景下按匿名处理，不报错） */
    private Claims tryParse(String token, String path) {
        try {
            return Jwts.parser()
                    .verifyWith(key)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
        } catch (JwtException | IllegalArgumentException e) {
            log.debug("白名单请求令牌无效，按匿名处理, path={}, reason={}", path, e.getMessage());
            return null;
        }
    }

    /** 把 userId / role 写进请求头，覆盖客户端可能伪造的同名头 */
    private ServerHttpRequest withIdentity(ServerHttpRequest request, Claims claims) {
        String userId = claims.get("userId", String.class);
        String role = claims.get("role", String.class);
        return request.mutate()
                .headers(headers -> {
                    headers.remove(HEADER_USER_ID);
                    headers.remove(HEADER_USER_ROLE);
                    if (userId != null && !userId.isBlank()) {
                        headers.set(HEADER_USER_ID, userId);
                    }
                    if (role != null) {
                        headers.set(HEADER_USER_ROLE, role);
                    }
                })
                .build();
    }

    private boolean isWhiteListed(HttpMethod method, String path) {
        // 浏览器跨域预检不携带令牌，直接放行
        if (method == HttpMethod.OPTIONS) {
            return true;
        }
        if (method == HttpMethod.POST
                && PUBLIC_POST.stream().anyMatch(pattern -> MATCHER.match(pattern, path))) {
            return true;
        }
        if (method != HttpMethod.GET) {
            return false;
        }
        if (PUBLIC_GET.stream().anyMatch(pattern -> MATCHER.match(pattern, path))) {
            return true;
        }
        if ("/api/products".equals(path)) {
            return true;
        }
        Matcher matcher = PRODUCT_DETAIL_PATH.matcher(path);
        return matcher.matches() && !PRODUCT_RESERVED_SEGMENTS.contains(matcher.group(1));
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
