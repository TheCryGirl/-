package com.mall.cart.config;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.filter.OncePerRequestFilter;
import java.util.concurrent.atomic.AtomicBoolean;
import static org.junit.jupiter.api.Assertions.*;

class ServiceIngressBoundaryTest {
    private OncePerRequestFilter filter(String token) throws Exception {
        return (OncePerRequestFilter) Class.forName("com.mall.cart.config.ServiceIngressFilter")
                .getConstructor(String.class).newInstance(token);
    }

    @Test
    void forgedIdentityWithoutTrustedTokenNeverReachesController() throws Exception {
        for (String supplied : new String[]{"", "wrong"}) {
            var request = new MockHttpServletRequest("GET", "/users");
            request.addHeader("X-User-Id", "forged"); request.addHeader("X-User-Role", "ADMIN");
            request.addHeader("X-Internal-Token", supplied);
            var response = new MockHttpServletResponse();
            AtomicBoolean called = new AtomicBoolean();
            filter("test-internal-token").doFilter(request, response, (a,b) -> called.set(true));
            assertFalse(called.get()); assertEquals(403, response.getStatus());
        }
    }

    @Test
    void emptyConfigurationStillFailsClosed() throws Exception {
        var response = new MockHttpServletResponse();
        filter("").doFilter(new MockHttpServletRequest("GET", "/products"), response,
                (a,b) -> fail("Empty configuration must not trust a direct caller"));
        assertEquals(403, response.getStatus());
    }

    @Test
    void matchingGatewayOrFeignTokenIsAllowedAndHealthStaysReachable() throws Exception {
        for (String path : new String[]{"/products/P1", "/actuator/health"}) {
            var request = new MockHttpServletRequest("GET", path);
            if (!path.equals("/actuator/health")) request.addHeader("X-Internal-Token", "test-internal-token");
            AtomicBoolean called = new AtomicBoolean();
            filter("test-internal-token").doFilter(request, new MockHttpServletResponse(), (a,b) -> called.set(true));
            assertTrue(called.get());
        }
    }
}