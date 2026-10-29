package com.mall.order.controller;

import com.mall.order.common.BizException;
import com.mall.order.common.ErrorCode;
import com.mall.order.common.PageResult;
import com.mall.order.common.Result;
import com.mall.order.dto.response.EvaluationResponse;
import com.mall.order.dto.response.EvaluationStatsResponse;
import com.mall.order.service.EvaluationService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 商品评价展示与评价管理接口（任务 #57）。 */
@RestController
@RequestMapping({"/evaluations", "/api/evaluations"})
@RequiredArgsConstructor
public class EvaluationController {

    private final EvaluationService evaluationService;

    @GetMapping("/products/{productId}")
    public Result<PageResult<EvaluationResponse>> listByProduct(
            @PathVariable String productId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {
        return Result.success(evaluationService.listByProduct(productId, page, size));
    }

    @GetMapping("/products/{productId}/stats")
    public Result<EvaluationStatsResponse> stats(@PathVariable String productId) {
        return Result.success(evaluationService.stats(productId));
    }

    @GetMapping
    public Result<PageResult<EvaluationResponse>> adminList(
            @RequestHeader(value = "X-User-Role", required = false) String role,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String productId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        requireAdmin(role);
        return Result.success(evaluationService.adminList(status, productId, page, size));
    }

    @PutMapping("/{evaluationId}/hide")
    public Result<Void> hide(
            @RequestHeader(value = "X-User-Id", required = false) String operatorId,
            @RequestHeader(value = "X-User-Role", required = false) String role,
            @PathVariable String evaluationId) {
        evaluationService.hide(evaluationId, operatorId, role);
        return Result.success();
    }

    @PutMapping("/{evaluationId}/show")
    public Result<Void> show(
            @RequestHeader(value = "X-User-Id", required = false) String operatorId,
            @RequestHeader(value = "X-User-Role", required = false) String role,
            @PathVariable String evaluationId) {
        evaluationService.show(evaluationId, operatorId, role);
        return Result.success();
    }

    private static void requireAdmin(String role) {
        if (!"ADMIN".equalsIgnoreCase(role)) {
            throw new BizException(ErrorCode.FORBIDDEN, "无权限操作评价");
        }
    }
}
