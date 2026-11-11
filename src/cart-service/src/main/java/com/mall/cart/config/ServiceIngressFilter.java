package com.mall.cart.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/** Business identity headers are trusted only on authenticated gateway/service calls. */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class ServiceIngressFilter extends OncePerRequestFilter {
    private final String expectedToken;

    public ServiceIngressFilter(@Value("${cart.internal-token:${INTERNAL_TOKEN:}}") String expectedToken) {
        this.expectedToken = expectedToken;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String path = request.getRequestURI().substring(request.getContextPath().length());
        // The launcher/container probes only health; no business route or general actuator exemption.
        if ("GET".equals(request.getMethod()) && "/actuator/health".equals(path)) {
            chain.doFilter(request, response);
            return;
        }
        String supplied = request.getHeader("X-Internal-Token");
        if (expectedToken == null || expectedToken.isBlank() || supplied == null
                || !MessageDigest.isEqual(expectedToken.getBytes(StandardCharsets.UTF_8),
                                          supplied.getBytes(StandardCharsets.UTF_8))) {
            response.setStatus(403);
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write("{\"code\":10003,\"message\":\"Untrusted service caller\",\"data\":null}");
            return;
        }
        chain.doFilter(request, response);
    }
}
