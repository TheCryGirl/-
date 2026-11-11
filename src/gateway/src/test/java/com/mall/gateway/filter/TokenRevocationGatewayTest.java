package com.mall.gateway.filter;

import com.mall.gateway.auth.UserAuthStateVerifier;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Test;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.test.util.ReflectionTestUtils;
import reactor.core.publisher.Mono;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class TokenRevocationGatewayTest {
    static final String KEY="test-secret-key-for-unit-test-0123456789abcd";
    String token() { return Jwts.builder().claim("userId","U1").claim("role","ADMIN").claim("tokenVersion",0L)
        .expiration(new Date(System.currentTimeMillis()+60000)).signWith(Keys.hmacShaKeyFor(KEY.getBytes(StandardCharsets.UTF_8))).compact(); }
    JwtAuthFilter filter(Mono<Boolean> answer) {
        var f=new JwtAuthFilter(KEY);var verifier=mock(UserAuthStateVerifier.class);
        when(verifier.isCurrent(any())).thenReturn(answer);ReflectionTestUtils.setField(f,"authStateVerifier",verifier);return f;
    }
    @Test void revokedOrUnavailableAccountIsRejectedOnProtectedRoute() {
        for(Mono<Boolean> answer:java.util.List.of(Mono.just(false),Mono.<Boolean>error(new IllegalStateException("down")))) {
            var x=MockServerWebExchange.from(MockServerHttpRequest.get("/api/users/me").header("Authorization","Bearer "+token()).build());
            filter(answer).filter(x,e->{fail("Revoked token reached protected service");return Mono.empty();}).block();
            assertEquals(401,x.getResponse().getStatusCode().value());
        }
    }
    @Test void revokedIdentityIsRemovedOnPublicRead() {
        var x=MockServerWebExchange.from(MockServerHttpRequest.get("/api/products/P1").header("Authorization","Bearer "+token())
            .header("X-User-Role","ADMIN").build());
        AtomicInteger calls=new AtomicInteger();
        filter(Mono.just(false)).filter(x,e->{calls.incrementAndGet();assertNull(e.getRequest().getHeaders().getFirst("X-User-Role"));return Mono.empty();}).block();
        assertEquals(1,calls.get());
    }
    @Test void downstreamErrorNeverRetriesAsAnonymous() {
        var x=MockServerWebExchange.from(MockServerHttpRequest.get("/api/products/P1").header("Authorization","Bearer "+token()).build());
        AtomicInteger calls=new AtomicInteger();
        assertThrows(IllegalStateException.class,()->filter(Mono.just(true)).filter(x,e->{calls.incrementAndGet();return Mono.error(new IllegalStateException("business error"));}).block());
        assertEquals(1,calls.get());
    }
}
