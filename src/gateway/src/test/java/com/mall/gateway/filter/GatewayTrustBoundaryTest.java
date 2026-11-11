package com.mall.gateway.filter;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.test.util.ReflectionTestUtils;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.jupiter.api.Assertions.*;

class GatewayTrustBoundaryTest {
    private static final String JWT = "test-secret-key-for-unit-test-0123456789abcd";
    private static final String INTERNAL = "test-cluster-internal-token";

    private JwtAuthFilter filter() {
        JwtAuthFilter f = new JwtAuthFilter(JWT);
        ReflectionTestUtils.setField(f, "internalToken", INTERNAL);
        var state = org.mockito.Mockito.mock(com.mall.gateway.auth.UserAuthStateVerifier.class);
        org.mockito.Mockito.when(state.isCurrent(org.mockito.ArgumentMatchers.any())).thenReturn(Mono.just(true));
        ReflectionTestUtils.setField(f, "authStateVerifier", state);
        return f;
    }

    @Test
    void anonymousAndInvalidJwtNeverForwardClientIdentityOrInternalToken() {
        for (String auth : new String[]{"", "Bearer invalid"}) {
            var request = MockServerHttpRequest.get("/api/products/P1")
                    .header("X-User-Id", "forged-owner").header("X-User-Role", "ADMIN")
                    .header("X-Internal-Token", "client-secret").header(HttpHeaders.AUTHORIZATION, auth).build();
            var exchange = MockServerWebExchange.from(request);
            AtomicReference<HttpHeaders> forwarded = new AtomicReference<>();
            filter().filter(exchange, e -> { forwarded.set(e.getRequest().getHeaders()); return Mono.empty(); }).block();
            assertNotNull(forwarded.get());
            assertNull(forwarded.get().getFirst("X-User-Id"));
            assertNull(forwarded.get().getFirst("X-User-Role"));
            assertEquals(INTERNAL, forwarded.get().getFirst("X-Internal-Token"));
        }
    }

    @Test
    void authenticatedIdentityComesFromJwtAndNeverFromClientHeaders() {
        String token = Jwts.builder().claim("userId", "real-user").claim("role", "BUYER")
                .expiration(new Date(System.currentTimeMillis()+60000))
                .signWith(Keys.hmacShaKeyFor(JWT.getBytes(StandardCharsets.UTF_8))).compact();
        var request = MockServerHttpRequest.get("/api/users/me")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .header("X-User-Id", "other").header("X-User-Role", "ADMIN").build();
        AtomicReference<HttpHeaders> forwarded = new AtomicReference<>();
        filter().filter(MockServerWebExchange.from(request), e -> {
            forwarded.set(e.getRequest().getHeaders()); return Mono.empty();
        }).block();
        assertEquals("real-user", forwarded.get().getFirst("X-User-Id"));
        assertEquals("BUYER", forwarded.get().getFirst("X-User-Role"));
        assertEquals(INTERNAL, forwarded.get().getFirst("X-Internal-Token"));
    }

    @Test
    void internalMutationPathsAreNeverPublishedEvenWithValidUserJwt() {
        String token = Jwts.builder().claim("userId", "user").claim("role", "ADMIN")
                .expiration(new Date(System.currentTimeMillis()+60000))
                .signWith(Keys.hmacShaKeyFor(JWT.getBytes(StandardCharsets.UTF_8))).compact();
        for (String path : new String[]{"/api/products/skus/reserve", "/api/products/skus/release",
                "/api/products/skus/confirm", "/api/products/skus/refund-restock",
                "/api/products/internal/seller-visibility", "/api/orders/O1/paid", "/api/orders/O1/refunded"}) {
            var exchange = MockServerWebExchange.from(MockServerHttpRequest.post(path)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + token).build());
            GatewayFilterChain chain = e -> { fail("Internal path reached downstream: " + path); return Mono.empty(); };
            filter().filter(exchange, chain).block();
            assertEquals(403, exchange.getResponse().getStatusCode().value());
        }
    }
}
