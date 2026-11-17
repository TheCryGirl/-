package com.mall.payment.controller;

import com.mall.payment.common.BizException;
import com.mall.payment.common.ChannelType;
import com.mall.payment.common.ErrorCode;
import com.mall.payment.common.PageResult;
import com.mall.payment.common.Result;
import com.mall.payment.entity.ReconcileBatch;
import com.mall.payment.entity.ReconcileDifference;
import com.mall.payment.service.AuditLogPublisher;
import com.mall.payment.service.ChannelReconcileService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Locale;
import java.util.Map;

/**
 * 渠道对账管理接口（#137，仅 ADMIN）。只出差异清单，不自动修账。
 */
@RestController
@RequestMapping({"/payments/admin/reconcile", "/api/payments/admin/reconcile"})
@RequiredArgsConstructor
public class ChannelReconcileController {

    private static final String ROLE_ADMIN = "ADMIN";

    private final ChannelReconcileService reconcileService;
    private final AuditLogPublisher auditLogPublisher;

    @PostMapping("/import")
    public Result<ReconcileBatch> importBill(
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @RequestHeader(value = "X-User-Role", required = false) String role,
            @RequestParam("channel") String channel,
            @RequestParam("file") MultipartFile file) {
        requireAdmin(userId, role);
        if (file == null || file.isEmpty()) {
            throw new BizException(ErrorCode.PARAM_INVALID, "账单文件不能为空");
        }
        ChannelType type = parseChannel(channel);
        try {
            ReconcileBatch batch = reconcileService.importBill(
                    userId, type, file.getOriginalFilename(), file.getInputStream());
            auditLogPublisher.publish(userId, role, "RECONCILE_IMPORT", "RECONCILE_BATCH",
                    batch.getBatchId(), "导入渠道账单，差异 " + batch.getDiffCount() + " 条");
            return Result.success(batch);
        } catch (IOException e) {
            throw new BizException(ErrorCode.PARAM_INVALID, "账单文件读取失败");
        }
    }

    @GetMapping("/batches")
    public Result<PageResult<ReconcileBatch>> batches(
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @RequestHeader(value = "X-User-Role", required = false) String role,
            @RequestParam(required = false) String channel,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        requireAdmin(userId, role);
        return Result.success(reconcileService.listBatches(channel, page, size));
    }

    @GetMapping("/differences")
    public Result<PageResult<ReconcileDifference>> differences(
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @RequestHeader(value = "X-User-Role", required = false) String role,
            @RequestParam(required = false) String batchId,
            @RequestParam(required = false) String diffType,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        requireAdmin(userId, role);
        return Result.success(reconcileService.listDifferences(batchId, diffType, status, page, size));
    }

    @PutMapping("/differences/{differenceId}/resolve")
    public Result<ReconcileDifference> resolve(
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @RequestHeader(value = "X-User-Role", required = false) String role,
            @PathVariable("differenceId") Long differenceId,
            @RequestBody(required = false) Map<String, String> body) {
        requireAdmin(userId, role);
        String remark = body == null ? null : body.get("remark");
        ReconcileDifference resolved = reconcileService.resolve(differenceId, userId, remark);
        auditLogPublisher.publish(userId, role, "RECONCILE_RESOLVE", "RECONCILE_DIFFERENCE",
                String.valueOf(differenceId), "标记对账差异已处理");
        return Result.success(resolved);
    }

    private static void requireAdmin(String userId, String role) {
        if (!ROLE_ADMIN.equals(role)) {
            throw new BizException(ErrorCode.FORBIDDEN, "仅管理员可操作渠道对账");
        }
        if (userId == null || userId.isBlank()) {
            throw new BizException(ErrorCode.UNAUTHORIZED, "未登录或用户信息缺失");
        }
    }

    private static ChannelType parseChannel(String channel) {
        try {
            return ChannelType.valueOf(channel.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new BizException(ErrorCode.PARAM_INVALID, "未知支付渠道: " + channel);
        }
    }
}