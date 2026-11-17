package com.mall.payment.client;

import com.mall.payment.client.dto.NotificationRequest;
import com.mall.payment.common.Result;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;

/** 调用 user-service 写站内通知（#139）。payment 的 Feign 配置不自动注入内部令牌，这里显式传。 */
@FeignClient(name = "user-service", contextId = "notificationClient", url = "${services.user-url:}")
public interface NotificationClient {

    @PostMapping("/internal/notifications")
    Result<Void> create(@RequestHeader("X-Internal-Token") String internalToken,
                        @RequestBody NotificationRequest request);
}