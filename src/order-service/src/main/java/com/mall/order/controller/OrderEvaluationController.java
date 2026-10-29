package com.mall.order.controller;

import com.mall.order.common.Result;
import com.mall.order.dto.request.CreateEvaluationRequest;
import com.mall.order.dto.response.EvaluationResponse;
import com.mall.order.service.EvaluationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** 订单维度的评价接口（任务 #57）。 */
@RestController
@RequestMapping({"/orders", "/api/orders"})
@RequiredArgsConstructor
public class OrderEvaluationController {

    private final EvaluationService evaluationService;

    @PostMapping("/{orderId}/evaluations")
    public Result<EvaluationResponse> create(
            @RequestHeader(value = "X-User-Id", required = false) String buyerId,
            @PathVariable String orderId,
            @Valid @RequestBody CreateEvaluationRequest request) {
        return Result.success(evaluationService.create(buyerId, orderId, request));
    }

    @GetMapping("/{orderId}/evaluations")
    public Result<List<EvaluationResponse>> list(
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @RequestHeader(value = "X-User-Role", required = false) String role,
            @PathVariable String orderId) {
        return Result.success(evaluationService.listByOrder(orderId, userId, role));
    }
}
