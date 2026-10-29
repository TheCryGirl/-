package com.mall.order.controller;

import com.mall.order.common.BizException;
import com.mall.order.common.ErrorCode;
import com.mall.order.common.PageResult;
import com.mall.order.common.Result;
import com.mall.order.config.InternalTokenVerifier;
import com.mall.order.dto.request.AuditLogRequest;
import com.mall.order.entity.AuditLog;
import com.mall.order.service.AuditLogService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 操作审计日志接口（任务 #58）：内部写入 + 管理员查询。 */
@RestController
@RequiredArgsConstructor
public class AuditLogController {

    private final AuditLogService auditLogService;
    private final InternalTokenVerifier internalTokenVerifier;

    @PostMapping("/internal/audit-logs")
    public Result<Void> write(
            @RequestHeader(value = "X-Internal-Token", required = false) String token,
            @RequestBody AuditLogRequest request) {
        internalTokenVerifier.verify(token);
        auditLogService.record(request);
        return Result.success();
    }

    @GetMapping({"/audit-logs", "/api/audit-logs"})
    public Result<PageResult<AuditLog>> list(
            @RequestHeader(value = "X-User-Role", required = false) String role,
            @RequestParam(required = false) String operatorId,
            @RequestParam(required = false) String action,
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        if (!"ADMIN".equalsIgnoreCase(role)) {
            throw new BizException(ErrorCode.FORBIDDEN, "无权限查看审计日志");
        }
        return Result.success(auditLogService.list(operatorId, action, from, to, page, size));
    }
}
