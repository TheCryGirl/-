package com.mall.payment.controller;

import com.mall.payment.common.BizException;
import com.mall.payment.common.ErrorCode;
import com.mall.payment.common.PageResult;
import com.mall.payment.common.Result;
import com.mall.payment.dto.request.AfterSaleRemarkRequest;
import com.mall.payment.dto.request.ArbitrateRequest;
import com.mall.payment.dto.request.AuditAfterSaleRequest;
import com.mall.payment.dto.request.CreateAfterSaleRequest;
import com.mall.payment.dto.request.ReshipRequest;
import com.mall.payment.dto.request.ReturnShipmentRequest;
import com.mall.payment.dto.request.SupplementRequest;
import com.mall.payment.dto.response.AfterSaleResponse;
import com.mall.payment.service.AfterSaleService;
import jakarta.validation.Valid;
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

import java.util.List;

/**
 * 售后中心接口。
 *
 * <p>与项目其他服务一致，同时映射带 /api 与不带 /api 的路径。
 * #143 增量：/after-sales 与 /api/after-sales 为 Issue 约定路径别名，
 * 由网关 payment-service 路由 Path 谓词一并转发。
 */
@RestController
@RequestMapping({"/payments/after-sales", "/api/payments/after-sales",
        "/after-sales", "/api/after-sales"})
@RequiredArgsConstructor
public class AfterSaleController {

    private static final String ROLE_SELLER = "SELLER";
    private static final String ROLE_ADMIN = "ADMIN";

    private final AfterSaleService afterSaleService;

    @PostMapping
    public Result<AfterSaleResponse> create(
            @RequestHeader("X-User-Id") String buyerId,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody CreateAfterSaleRequest request) {
        return Result.success(afterSaleService.create(buyerId, idempotencyKey, request));
    }

    @GetMapping
    public Result<PageResult<AfterSaleResponse>> list(
            @RequestHeader("X-User-Id") String userId,
            @RequestHeader(value = "X-User-Role", required = false) String role,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String type,
            @RequestParam(required = false) String orderId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        return Result.success(afterSaleService.list(userId, role, status, type,
                orderId, page, size));
    }

    @GetMapping("/{ticketId}")
    public Result<AfterSaleResponse> detail(
            @RequestHeader("X-User-Id") String userId,
            @RequestHeader(value = "X-User-Role", required = false) String role,
            @PathVariable("ticketId") String ticketId) {
        return Result.success(afterSaleService.detail(userId, role, ticketId));
    }

    @PutMapping("/{ticketId}/audit")
    public Result<AfterSaleResponse> audit(
            @RequestHeader("X-User-Id") String operatorId,
            @RequestHeader(value = "X-User-Role", required = false) String role,
            @PathVariable("ticketId") String ticketId,
            @Valid @RequestBody AuditAfterSaleRequest request) {
        requireSellerOrAdmin(role);
        return Result.success(afterSaleService.audit(
                ticketId, operatorId, role, request.getApproved(), request.getRemark()));
    }

    @PutMapping("/{ticketId}/return-shipment")
    public Result<AfterSaleResponse> shipReturn(
            @RequestHeader("X-User-Id") String buyerId,
            @PathVariable("ticketId") String ticketId,
            @Valid @RequestBody ReturnShipmentRequest request) {
        return Result.success(afterSaleService.shipReturn(ticketId, buyerId, request.getTrackingNo()));
    }

    @PutMapping("/{ticketId}/receive")
    public Result<AfterSaleResponse> receiveReturn(
            @RequestHeader("X-User-Id") String operatorId,
            @RequestHeader(value = "X-User-Role", required = false) String role,
            @PathVariable("ticketId") String ticketId,
            @RequestBody(required = false) AfterSaleRemarkRequest request) {
        requireSellerOrAdmin(role);
        String remark = request == null ? null : request.getRemark();
        return Result.success(afterSaleService.receiveReturn(
                ticketId, operatorId, role, remark));
    }

    @PutMapping("/{ticketId}/reship")
    public Result<AfterSaleResponse> reshipExchange(
            @RequestHeader("X-User-Id") String operatorId,
            @RequestHeader(value = "X-User-Role", required = false) String role,
            @PathVariable("ticketId") String ticketId,
            @Valid @RequestBody ReshipRequest request) {
        requireSellerOrAdmin(role);
        return Result.success(afterSaleService.reshipExchange(
                ticketId, operatorId, role, request.getTrackingNo(), request.getRemark()));
    }

    /** #143 上传凭证（先上传后引用），单次最多 3 张，返回站内路径列表 */
    @PostMapping("/attachments/upload")
    public Result<List<String>> uploadAttachments(
            @RequestHeader("X-User-Id") String userId,
            @RequestParam("files") List<MultipartFile> files) {
        return Result.success(afterSaleService.uploadAttachments(userId, files));
    }

    /** #143 买家撤销：仅 PENDING 可撤销 */
    @PostMapping("/{ticketId}/cancel")
    public Result<AfterSaleResponse> cancel(
            @RequestHeader("X-User-Id") String buyerId,
            @PathVariable("ticketId") String ticketId,
            @RequestBody(required = false) AfterSaleRemarkRequest request) {
        String remark = request == null ? null : request.getRemark();
        return Result.success(afterSaleService.cancel(ticketId, buyerId, remark));
    }

    /** #143 买家申请平台仲裁：仅卖家 REJECTED 后可申请 */
    @PostMapping("/{ticketId}/arbitration-request")
    public Result<AfterSaleResponse> requestArbitration(
            @RequestHeader("X-User-Id") String buyerId,
            @PathVariable("ticketId") String ticketId,
            @RequestBody(required = false) AfterSaleRemarkRequest request) {
        String remark = request == null ? null : request.getRemark();
        return Result.success(afterSaleService.requestArbitration(ticketId, buyerId, remark));
    }

    /** #143 仲裁期间双方补充说明与凭证，材料对双方可见 */
    @PostMapping("/{ticketId}/supplement")
    public Result<AfterSaleResponse> supplement(
            @RequestHeader("X-User-Id") String userId,
            @RequestHeader(value = "X-User-Role", required = false) String role,
            @PathVariable("ticketId") String ticketId,
            @Valid @RequestBody SupplementRequest request) {
        return Result.success(afterSaleService.supplement(ticketId, userId, role, request));
    }

    /** #143 管理员平台仲裁裁定：仅 ADMIN，理由必填 */
    @PostMapping("/{ticketId}/arbitrate")
    public Result<AfterSaleResponse> arbitrate(
            @RequestHeader("X-User-Id") String operatorId,
            @RequestHeader(value = "X-User-Role", required = false) String role,
            @PathVariable("ticketId") String ticketId,
            @Valid @RequestBody ArbitrateRequest request) {
        requireAdmin(role);
        return Result.success(afterSaleService.arbitrate(ticketId, operatorId, role, request));
    }

    private void requireSellerOrAdmin(String role) {
        if (!ROLE_SELLER.equals(role) && !ROLE_ADMIN.equals(role)) {
            throw new BizException(ErrorCode.FORBIDDEN, "无权限处理售后单");
        }
    }

    private void requireAdmin(String role) {
        if (!ROLE_ADMIN.equals(role)) {
            throw new BizException(ErrorCode.FORBIDDEN, "无权限执行平台仲裁");
        }
    }
}
