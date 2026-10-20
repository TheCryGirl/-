package com.mall.gateway;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

/**
 * 在线商城 API 网关。
 *
 * <p>所有前端请求的唯一入口，职责：
 * <ul>
 *   <li>按服务名路由到 Nacos 注册的微服务</li>
 *   <li>统一校验 JWT，校验通过后把 userId 放进请求头传给下游</li>
 *   <li>统一跨域处理</li>
 *   <li>统一异常返回结构</li>
 * </ul>
 */
@SpringBootApplication
@EnableDiscoveryClient
public class GatewayApplication {

    public static void main(String[] args) {
        SpringApplication.run(GatewayApplication.class, args);
    }
}
