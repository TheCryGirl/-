package com.mall.payment.controller;

import com.mall.payment.common.Result;
import com.mall.payment.dto.request.MockDuplicateRequest;
import com.mall.payment.dto.request.MockPayRequest;
import com.mall.payment.dto.request.MockRefundRequest;
import com.mall.payment.dto.response.MockDuplicateResponse;
import com.mall.payment.dto.response.MockPayResponse;
import com.mall.payment.dto.response.RefundResponse;
import com.mall.payment.service.MockPaymentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 开发与测试用的 MOCK 支付入口。
 */
@RestController
@RequestMapping({"/payments/mock", "/api/payments/mock"})
@RequiredArgsConstructor
public class MockPaymentController {

    private final MockPaymentService mockPaymentService;

    @PostMapping("/pay")
    public Result<MockPayResponse> mockPay(
            @RequestHeader("X-User-Id") String buyerId,
            @Valid @RequestBody MockPayRequest request) {
        return Result.success(mockPaymentService.mockPay(buyerId, request));
    }

    @PostMapping("/duplicate")
    public Result<MockDuplicateResponse> duplicate(
            @RequestHeader("X-User-Id") String userId,
            @Valid @RequestBody MockDuplicateRequest request) {
        return Result.success(mockPaymentService.duplicateCallback(request));
    }

    @PostMapping("/refund")
    public Result<RefundResponse> mockRefund(
            @RequestHeader("X-User-Id") String buyerId,
            @Valid @RequestBody MockRefundRequest request) {
        return Result.success(mockPaymentService.mockRefund(buyerId, request));
    }
}
