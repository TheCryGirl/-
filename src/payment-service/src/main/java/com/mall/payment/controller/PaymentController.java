package com.mall.payment.controller;

import com.mall.payment.common.Result;
import com.mall.payment.dto.request.CreatePaymentRequest;
import com.mall.payment.dto.request.PaymentCallbackRequest;
import com.mall.payment.dto.response.CreatePaymentResponse;
import com.mall.payment.dto.response.PaymentDetailResponse;
import com.mall.payment.service.CallbackResult;
import com.mall.payment.service.PaymentCallbackService;
import com.mall.payment.service.PaymentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 支付单接口。服务内部路径不带 /api，与《05-接口契约》1.2 节一致。
 */
@RestController
@RequestMapping("/payments")
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentService paymentService;
    private final PaymentCallbackService paymentCallbackService;

    @PostMapping
    public Result<CreatePaymentResponse> create(
            @RequestHeader("X-User-Id") String buyerId,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody CreatePaymentRequest request) {
        return Result.success(paymentService.createPayment(buyerId, idempotencyKey, request));
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
}
