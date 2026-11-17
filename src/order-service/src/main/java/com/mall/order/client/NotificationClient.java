package com.mall.order.client;

import com.mall.order.client.dto.NotificationRequest;
import com.mall.order.common.Result;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

/** 调用 user-service 写站内通知（#139）。X-Internal-Token 由 TraceFeignConfig 统一注入。 */
@FeignClient(name = "user-service", contextId = "notificationClient", url = "${services.user-url:}")
public interface NotificationClient {

    @PostMapping("/internal/notifications")
    Result<Void> create(@RequestBody NotificationRequest request);
}