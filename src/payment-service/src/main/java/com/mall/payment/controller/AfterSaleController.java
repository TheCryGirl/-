package com.mall.payment.controller;

import com.mall.payment.common.BizException;
import com.mall.payment.common.ErrorCode;
import com.mall.payment.common.PageResult;
import com.mall.payment.common.Result;
import com.mall.payment.dto.request.AfterSaleRemarkRequest;
import com.mall.payment.dto.request.AuditAfterSaleRequest;
import com.mall.payment.dto.request.CreateAfterSaleRequest;
import com.mall.payment.dto.request.ReshipRequest;
import com.mall.payment.dto.request.ReturnShipmentRequest;
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

/**
 * 售后中心接口。
 *
 * <p>与项目其他服务一致，同时映射带 /api 与不带 /api 的路径。
 */
@RestController
@RequestMapping({"/payments/after-sales", "/api/payments/after-sales"})
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

    private void requireSellerOrAdmin(String role) {
        if (!ROLE_SELLER.equals(role) && !ROLE_ADMIN.equals(role)) {
            throw new BizException(ErrorCode.FORBIDDEN, "无权限处理售后单");
        }
    }
}
