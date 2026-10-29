package com.mall.product.client;

import com.mall.product.client.dto.AuditLogRequest;
import com.mall.product.common.Result;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;

/**
 * 订单服务的审计日志内部接口（任务 #58）。
 *
 * <p>审计中心放在 order-service，其他服务的操作日志统一上报到这里。
 */
@FeignClient(name = "order-service", url = "${services.order-url:}", contextId = "auditClient")
public interface AuditClient {

    @PostMapping("/internal/audit-logs")
    Result<Void> write(@RequestHeader("X-Internal-Token") String internalToken,
                       @RequestBody AuditLogRequest request);
}
