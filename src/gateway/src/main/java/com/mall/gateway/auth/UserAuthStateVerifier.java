package com.mall.gateway.auth;

import io.jsonwebtoken.Claims;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import java.time.Duration;

/** No positive cache: a committed freeze/role change applies to subsequent checks. */
@Component
public class UserAuthStateVerifier {
    private final WebClient client;
    private final String token;
    private final String baseUrl;

    public UserAuthStateVerifier(WebClient.Builder builder,
            @Value("${gateway.internal-token:${INTERNAL_TOKEN:}}") String token,
            @Value("${gateway.auth-state-url:http://user-service}") String baseUrl) {
        this.client = builder.build(); this.token = token; this.baseUrl = baseUrl;
    }

    public Mono<Boolean> isCurrent(Claims claims) {
        String userId = claims.get("userId", String.class);
        String role = claims.get("role", String.class);
        Object version = claims.get("tokenVersion");
        // Pre-upgrade JWTs without a revocation version must log in again.
        if (userId == null || userId.isBlank() || role == null || !(version instanceof Number)
                || token == null || token.isBlank()) return Mono.just(false);
        return client.get().uri(baseUrl + "/users/internal/auth-state/{id}", userId)
                .header("X-Internal-Token", token).retrieve().bodyToMono(AuthResult.class)
                .map(result -> result.code == 0 && result.data != null
                        && userId.equals(result.data.userId) && role.equals(result.data.role)
                        && "ACTIVE".equals(result.data.status)
                        && ((Number)version).longValue() == result.data.tokenVersion)
                .defaultIfEmpty(false).timeout(Duration.ofSeconds(3));
    }

    public static class AuthResult {
        public int code;
        public AuthState data;
    }
    public static class AuthState {
        public String userId;
        public String role;
        public String status;
        public long tokenVersion;
    }
}
