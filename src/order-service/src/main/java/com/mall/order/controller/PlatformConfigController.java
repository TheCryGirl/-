package com.mall.order.controller;

import com.mall.order.common.BizException;
import com.mall.order.common.ErrorCode;
import com.mall.order.common.Result;
import com.mall.order.entity.ConfigAudit;
import com.mall.order.service.AuditLogService;
import com.mall.order.service.NacosConfigService;
import com.mall.order.service.PlatformConfigService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 平台配置管理接口（任务 #43），仅管理员可用。
 *
 * <p>身份由网关注入，这里只做角色判定；网关路由 /api/config/** 转发到 /config/**。
 */
@Slf4j
@RestController
@RequestMapping({"/config", "/api/config"})
@RequiredArgsConstructor
public class PlatformConfigController {

    private final PlatformConfigService platformConfigService;
    private final AuditLogService auditLogService;

    @GetMapping
    public Result<Map<String, Object>> get(
            @RequestHeader(value = "X-User-Role", required = false) String role) {
        requireAdmin(role);
        return Result.success(Map.of(
                "values", platformConfigService.current(),
                "labels", platformConfigService.keyLabels()));
    }

    @PutMapping
    public Result<Map<String, String>> update(
            @RequestHeader(value = "X-User-Id", required = false) String operatorId,
            @RequestHeader(value = "X-User-Role", required = false) String role,
            @RequestBody Map<String, String> changes) {
        requireAdmin(role);
        if (operatorId == null || operatorId.isBlank()) {
            throw new BizException(ErrorCode.UNAUTHORIZED, "未登录或用户信息缺失");
        }
        Map<String, String> updated = platformConfigService.update(changes, operatorId);
        try {
            auditLogService.record(operatorId, role, "CONFIG_UPDATE", "CONFIG",
                    NacosConfigService.DATA_ID, "修改配置项：" + String.join(",", changes.keySet()));
        } catch (Exception e) {
            log.warn("写入配置审计失败, operator={}, keys={}", operatorId, changes.keySet(), e);
        }
        return Result.success(updated);
    }

    @GetMapping("/history")
    public Result<List<ConfigAudit>> history(
            @RequestHeader(value = "X-User-Role", required = false) String role,
            @RequestParam(defaultValue = "20") int limit) {
        requireAdmin(role);
        return Result.success(platformConfigService.history(limit));
    }

    private void requireAdmin(String role) {
        if (!"ADMIN".equalsIgnoreCase(role)) {
            throw new BizException(ErrorCode.FORBIDDEN, "无权限操作平台配置");
        }
    }
}
