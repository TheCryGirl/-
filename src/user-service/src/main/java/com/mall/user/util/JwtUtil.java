package com.mall.user.util;

import com.mall.user.common.Role;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;

/**
 * JWT 签发与解析工具。
 *
 * <p>密钥从环境变量 {@code JWT_SECRET} 注入，禁止硬编码。
 */
@Slf4j
@Component
public class JwtUtil {

    private final SecretKey key;
    private final long expirationHours;

    public JwtUtil(@Value("${jwt.secret}") String secret,
                   @Value("${jwt.expiration-hours}") long expirationHours) {
        if (secret == null || secret.length() < 32) {
            throw new IllegalArgumentException("JWT_SECRET 长度至少 32 位，请从环境变量配置");
        }
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.expirationHours = expirationHours;
    }

    /**
     * 签发访问令牌。
     *
     * @param userId  用户ID
     * @param role    角色
     * @return JWT 字符串
     */
    public String generateToken(String userId, Role role) {
        Instant now = Instant.now();
        Instant expire = now.plus(expirationHours, ChronoUnit.HOURS);
        return Jwts.builder()
                .subject(userId)
                .claim("userId", userId)
                .claim("role", role.name())
                .issuedAt(Date.from(now))
                .expiration(Date.from(expire))
                .signWith(key)
                .compact();
    }

    /**
     * 解析并校验令牌。
     *
     * @param token JWT 字符串
     * @return 载荷
     * @throws JwtException 令牌无效或过期
     */
    public Claims parseToken(String token) {
        return Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}
