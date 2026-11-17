package com.mall.gateway.filter;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.YamlPropertiesFactoryBean;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpMethod;
import org.springframework.util.AntPathMatcher;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.*;

class MessageRouteTest {
    @Test
    void allMessageEndpointsReachUserServiceWithApiPrefixRemoved() {
        YamlPropertiesFactoryBean yaml = new YamlPropertiesFactoryBean();
        yaml.setResources(new ClassPathResource("application.yaml"));
        Properties properties = yaml.getObject();
        assertNotNull(properties);
        String route = "spring.cloud.gateway.routes[0].";
        assertEquals("user-service", properties.getProperty(route + "id"));
        assertEquals("lb://user-service", properties.getProperty(route + "uri"));
        assertEquals("StripPrefix=1", properties.getProperty(route + "filters[0]"));
        String[] paths = properties.getProperty(route + "predicates[0]").substring("Path=".length()).split(",");
        AntPathMatcher matcher = new AntPathMatcher();
        for (String endpoint : new String[]{"/api/messages", "/api/messages/conversations",
                "/api/messages/conversations/C1", "/api/messages/conversations/C1/read"}) {
            assertTrue(Arrays.stream(paths).anyMatch(path -> matcher.match(path, endpoint)), endpoint);
        }
    }

    @Test
    void messageReadsSendsAndReadReceiptsRequireLogin() throws Exception {
        JwtAuthFilter filter = new JwtAuthFilter("message-route-test-secret-0123456789abcdef");
        Method method = JwtAuthFilter.class.getDeclaredMethod("isWhiteListed", HttpMethod.class, String.class);
        method.setAccessible(true);
        assertFalse((boolean) method.invoke(filter, HttpMethod.GET, "/api/messages/conversations"));
        assertFalse((boolean) method.invoke(filter, HttpMethod.GET, "/api/messages/conversations/C1"));
        assertFalse((boolean) method.invoke(filter, HttpMethod.POST, "/api/messages"));
        assertFalse((boolean) method.invoke(filter, HttpMethod.PUT, "/api/messages/conversations/C1/read"));
    }
}
