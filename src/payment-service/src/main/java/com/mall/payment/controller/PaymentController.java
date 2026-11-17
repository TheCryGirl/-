package com.mall.payment.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mall.payment.channel.MockChannel;
import com.mall.payment.channel.PaymentChannelRegistry;
import com.mall.payment.common.BizException;
import com.mall.payment.common.ChannelType;
import com.mall.payment.common.ErrorCode;
import com.mall.payment.common.PageResult;
import com.mall.payment.common.Result;
import com.mall.payment.dto.request.AuditRefundRequest;
import com.mall.payment.dto.request.CreatePaymentRequest;
import com.mall.payment.dto.request.PaymentCallbackRequest;
import com.mall.payment.dto.request.RefundRequest;
import com.mall.payment.dto.response.AdminFinanceSummaryResponse;
import com.mall.payment.dto.response.CreatePaymentResponse;
import com.mall.payment.dto.response.PaymentDetailResponse;
import com.mall.payment.dto.response.RefundResponse;
import com.mall.payment.service.AuditLogPublisher;
import com.mall.payment.service.CallbackResult;
import com.mall.payment.service.ChannelCallbackOutcome;
import com.mall.payment.service.ChannelCallbackService;
import com.mall.payment.service.FinanceSummaryService;
import com.mall.payment.service.PaymentCallbackService;
import com.mall.payment.service.NotificationPublisher;
import com.mall.payment.service.PaymentService;
import com.mall.payment.service.RefundService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

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
    private final NotificationPublisher notificationPublisher;
    private final PaymentCallbackService paymentCallbackService;
    private final RefundService refundService;
    private final AuditLogPublisher auditLogPublisher;
    private final FinanceSummaryService financeSummaryService;
    private final ChannelCallbackService channelCallbackService;
    private final PaymentChannelRegistry channelRegistry;
    private final ObjectMapper objectMapper;

    /**
     * 管理员资金/风控汇总（退款口径）。
     *
     * <p>必须带 X-User-Id 与 X-User-Role: ADMIN，且角色严格等于 ADMIN；
     * 不接受 query 参数授权。路径写在 /{paymentId} 之前，避免被详情路由吞掉。
     */
    @GetMapping("/admin/finance-summary")
    public Result<AdminFinanceSummaryResponse> adminFinanceSummary(
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @RequestHeader(value = "X-User-Role", required = false) String role) {
        if (!ROLE_ADMIN.equals(role)) {
            throw new BizException(ErrorCode.FORBIDDEN, "仅管理员可查看资金汇总");
        }
        if (userId == null || userId.isBlank()) {
            throw new BizException(ErrorCode.UNAUTHORIZED, "未登录或用户信息缺失");
        }
        return Result.success(financeSummaryService.summary());
    }

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

    /**
     * 已启用渠道列表（#137）。支付页按此渲染渠道选项；沙箱渠道带 sandbox 标记。
     */
    @GetMapping("/channels")
    public Result<List<Map<String, Object>>> channels(@RequestHeader("X-User-Id") String userId) {
        List<Map<String, Object>> list = channelRegistry.enabledChannels().stream()
                .map(type -> Map.<String, Object>of(
                        "channel", type.name(),
                        "name", type == ChannelType.MOCK ? "模拟支付" : "支付宝（沙箱）",
                        "sandbox", true))
                .toList();
        return Result.success(list);
    }

    /**
     * 按渠道路由的支付回调（#137）。豁免网关鉴权，验签由渠道实现完成；
     * 支付宝按渠道规定返回纯文本 success/failure，MOCK 返回统一 Result。
     */
    @PostMapping("/callback/{channel}")
    public ResponseEntity<?> channelCallback(@PathVariable("channel") String channel,
                                             HttpServletRequest httpRequest) throws IOException {
        ChannelType type = parseChannel(channel);
        Map<String, String> params = readCallbackParams(type, httpRequest);
        ChannelCallbackOutcome outcome = channelCallbackService.processPayment(type, params);
        return channelResponse(type, outcome);
    }

    /**
     * 退款异步通知（#137）：验签 + 幂等，处理链与支付回调一致。
     */
    @PostMapping("/refund-callback/{channel}")
    public ResponseEntity<?> refundCallback(@PathVariable("channel") String channel,
                                            HttpServletRequest httpRequest) throws IOException {
        ChannelType type = parseChannel(channel);
        Map<String, String> params = readCallbackParams(type, httpRequest);
        ChannelCallbackOutcome outcome = channelCallbackService.processRefund(type, params);
        return channelResponse(type, outcome);
    }

    /**
     * 渠道退款失败/滞留时的人工重试（#137）。同一 refundId 是渠道幂等键，不会重复退款。
     */
    @PostMapping("/refunds/{refundId}/retry")
    public Result<RefundResponse> retryRefund(
            @RequestHeader("X-User-Id") String operatorId,
            @RequestHeader(value = "X-User-Role", required = false) String role,
            @PathVariable("refundId") String refundId) {
        if (!ROLE_SELLER.equals(role) && !ROLE_ADMIN.equals(role)) {
            throw new BizException(ErrorCode.FORBIDDEN, "无权限重试退款");
        }
        RefundResponse refund = refundService.retryRefund(refundId, operatorId, role);
        auditLogPublisher.publish(operatorId, role, "REFUND_RETRY", "REFUND", refundId, "人工重试渠道退款");
        return Result.success(refund);
    }

    @PostMapping("/{paymentId}/refund")
    public Result<RefundResponse> refund(
            @RequestHeader("X-User-Id") String buyerId,
            @PathVariable("paymentId") String paymentId,
            @Valid @RequestBody RefundRequest request) {
        RefundResponse refund = refundService.createRefund(buyerId, paymentId, request);
        if ("SUCCESS".equals(String.valueOf(refund.getStatus()))) {
            notificationPublisher.refundDone(refund.getBuyerId(), refund.getOrderId(), refund.getAmount());
        }
        return Result.success(refund);
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
        RefundResponse refund = refundService.auditRefund(
                refundId, request.getApproved(), auditorId, role, request.getRemark());
        if ("SUCCESS".equals(String.valueOf(refund.getStatus()))) {
            notificationPublisher.refundDone(refund.getBuyerId(), refund.getOrderId(), refund.getAmount());
        }
        auditLogPublisher.publish(auditorId, role, "REFUND_AUDIT", "REFUND", refundId,
                (Boolean.TRUE.equals(request.getApproved()) ? "通过退款" : "驳回退款") + suffix(request.getRemark()));
        return Result.success(refund);
    }

    @GetMapping("/refunds/{refundId}")
    public Result<RefundResponse> getRefund(
            @RequestHeader("X-User-Id") String userId,
            @RequestHeader(value = "X-User-Role", required = false) String role,
            @PathVariable("refundId") String refundId) {
        return Result.success(refundService.getRefund(userId, role, refundId));
    }

    private static String suffix(String remark) {
        return remark == null || remark.isBlank() ? "" : "：" + remark;
    }

    private static ChannelType parseChannel(String channel) {
        try {
            return ChannelType.valueOf(channel.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new BizException(ErrorCode.PARAM_INVALID, "未知支付渠道: " + channel);
        }
    }

    /** MOCK 渠道沿用 JSON 报文；支付宝异步通知是 form 参数 */
    private Map<String, String> readCallbackParams(ChannelType type, HttpServletRequest request)
            throws IOException {
        if (type == ChannelType.MOCK) {
            PaymentCallbackRequest body =
                    objectMapper.readValue(request.getInputStream(), PaymentCallbackRequest.class);
            return MockChannel.toParams(body);
        }
        Map<String, String> params = new HashMap<>();
        request.getParameterMap().forEach((key, values) -> {
            if (values != null && values.length > 0) {
                params.put(key, values[0]);
            }
        });
        return params;
    }

    private static ResponseEntity<?> channelResponse(ChannelType type, ChannelCallbackOutcome outcome) {
        if (type == ChannelType.ALIPAY) {
            return ResponseEntity.ok()
                    .contentType(MediaType.TEXT_PLAIN)
                    .body(outcome.success() ? "success" : "failure");
        }
        return ResponseEntity.ok(outcome.success()
                ? Result.success(null)
                : Result.fail(outcome.code(), outcome.message()));
    }
}
