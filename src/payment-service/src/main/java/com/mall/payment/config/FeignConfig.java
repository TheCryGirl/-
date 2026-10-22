package com.mall.payment.config;

import feign.RequestInterceptor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Feign 统一携带内部调用令牌，下游服务据此识别请求来自集群内部。
 */
@Configuration
public class FeignConfig {

    @Value("${payment.internal-token}")
    private String internalToken;

    @Bean
    public RequestInterceptor internalTokenInterceptor() {
        return template -> {
            if (internalToken != null && !internalToken.isBlank()) {
                template.header("X-Internal-Token", internalToken);
            }
        };
    }
}
