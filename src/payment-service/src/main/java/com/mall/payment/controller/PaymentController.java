package com.mall.payment.controller;

import com.mall.payment.common.BizException;
import com.mall.payment.common.ErrorCode;
import com.mall.payment.common.PageResult;
import com.mall.payment.common.Result;
import com.mall.payment.dto.request.AuditRefundRequest;
import com.mall.payment.dto.request.CreatePaymentRequest;
import com.mall.payment.dto.request.PaymentCallbackRequest;
import com.mall.payment.dto.request.RefundRequest;
import com.mall.payment.dto.response.CreatePaymentResponse;
import com.mall.payment.dto.response.PaymentDetailResponse;
import com.mall.payment.dto.response.RefundResponse;
import com.mall.payment.service.CallbackResult;
import com.mall.payment.service.PaymentCallbackService;
import com.mall.payment.service.PaymentService;
import com.mall.payment.service.RefundService;
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
 * 支付单接口。
 *
 * <p>同时映射带 /api 与不带 /api 的路径：项目其他服务也这样处理，
 * 用于兼容网关当前按 /api/** 原样转发、尚未统一 StripPrefix 的现状。
 */
@RestController
@RequestMapping({"/payments", "/api/payments"})
@RequiredArgsConstructor
public class PaymentController {

    private static final String ROLE_SELLER = "SELLER";
    private static final String ROLE_ADMIN = "ADMIN";

    private final PaymentService paymentService;
    private final PaymentCallbackService paymentCallbackService;
    private final RefundService refundService;

    @PostMapping
    public Result<CreatePaymentResponse> create(
            @RequestHeader("X-User-Id") String buyerId,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody CreatePaymentRequest request) {
        return Result.success(paymentService.createPayment(buyerId, idempotencyKey, request));
    }

    @GetMapping("/refunds")
    public Result<PageResult<RefundResponse>> listRefunds(
            @RequestHeader("X-User-Id") String userId,
            @RequestHeader(value = "X-User-Role", required = false) String role,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        return Result.success(refundService.listRefunds(userId, role, status, page, size));
    }

    @GetMapping("/{paymentId}")
    public Result<PaymentDetailResponse> getPayment(
            @RequestHeader("X-User-Id") String buyerId,
            @PathVariable("paymentId") String paymentId) {
        return Result.success(paymentService.getPayment(buyerId, paymentId));
    }

    @GetMapping("/order/{orderId}")
    public Result<PaymentDetailResponse> getPaymentByOrder(
            @RequestHeader("X-User-Id") String buyerId,
            @PathVariable("orderId") String orderId) {
        return Result.success(paymentService.getPaymentByOrderId(buyerId, orderId));
    }

    /**
     * 第三方支付回调，不需要登录，由签名保证来源可信。
     */
    @PostMapping("/callback")
    public Result<Void> callback(@Valid @RequestBody PaymentCallbackRequest request) {
        CallbackResult result = paymentCallbackService.process(request);
        return result.isSuccess()
                ? Result.success(null)
                : Result.fail(result.getCode(), result.getMessage());
    }

    @PostMapping("/{paymentId}/refund")
    public Result<RefundResponse> refund(
            @RequestHeader("X-User-Id") String buyerId,
            @PathVariable("paymentId") String paymentId,
            @Valid @RequestBody RefundRequest request) {
        return Result.success(refundService.createRefund(buyerId, paymentId, request));
    }

    @PutMapping("/refunds/{refundId}/audit")
    public Result<RefundResponse> auditRefund(
            @RequestHeader("X-User-Id") String auditorId,
            @RequestHeader(value = "X-User-Role", required = false) String role,
            @PathVariable("refundId") String refundId,
            @Valid @RequestBody AuditRefundRequest request) {
        if (!ROLE_SELLER.equals(role) && !ROLE_ADMIN.equals(role)) {
            throw new BizException(ErrorCode.FORBIDDEN, "无权限审核退款");
        }
        return Result.success(refundService.auditRefund(
                refundId, request.getApproved(), auditorId, role, request.getRemark()));
    }

    @GetMapping("/refunds/{refundId}")
    public Result<RefundResponse> getRefund(
            @RequestHeader("X-User-Id") String userId,
            @RequestHeader(value = "X-User-Role", required = false) String role,
            @PathVariable("refundId") String refundId) {
        return Result.success(refundService.getRefund(userId, role, refundId));
    }
}
