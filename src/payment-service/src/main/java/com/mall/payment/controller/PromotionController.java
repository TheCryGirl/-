package com.mall.payment.controller;

import com.mall.payment.common.BizException;
import com.mall.payment.common.ErrorCode;
import com.mall.payment.common.PageResult;
import com.mall.payment.common.Result;
import com.mall.payment.dto.request.PromotionCreateRequest;
import com.mall.payment.dto.request.PromotionUpdateRequest;
import com.mall.payment.dto.response.PromotionResponse;
import com.mall.payment.service.PromotionService;
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

import java.util.List;

/**
 * 满减/满折活动接口（#140）。管理端配置；生效清单为内部接口，供 order-service 计价链试算。
 */
@RestController
@RequestMapping({"/promotions", "/api/promotions"})
@RequiredArgsConstructor
public class PromotionController {

    private static final String ROLE_ADMIN = "ADMIN";

    private final PromotionService promotionService;

    @GetMapping
    public Result<PageResult<PromotionResponse>> adminList(
            @RequestHeader("X-User-Id") String userId,
            @RequestHeader(value = "X-User-Role", required = false) String role,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        requireAdmin(userId, role);
        return Result.success(promotionService.adminList(status, page, size));
    }

    @PostMapping
    public Result<PromotionResponse> adminCreate(
            @RequestHeader("X-User-Id") String userId,
            @RequestHeader(value = "X-User-Role", required = false) String role,
            @Valid @RequestBody PromotionCreateRequest request) {
        requireAdmin(userId, role);
        return Result.success(promotionService.adminCreate(userId, request));
    }

    @PutMapping("/{promotionId}")
    public Result<PromotionResponse> adminUpdate(
            @RequestHeader("X-User-Id") String userId,
            @RequestHeader(value = "X-User-Role", required = false) String role,
            @PathVariable("promotionId") String promotionId,
            @Valid @RequestBody PromotionUpdateRequest request) {
        requireAdmin(userId, role);
        return Result.success(promotionService.adminUpdate(userId, promotionId, request));
    }

    private void requireAdmin(String userId, String role) {
        if (!ROLE_ADMIN.equals(role)) {
            throw new BizException(ErrorCode.FORBIDDEN, "无权限维护满减活动");
        }
    }

    /** 内部接口（#140）：计价链读取当前生效中的活动 */
    @RestController
    @RequestMapping("/internal/promotions")
    @RequiredArgsConstructor
    public static class InternalPromotionController {

        private final PromotionService promotionService;

        @GetMapping("/active")
        public Result<List<PromotionResponse>> active() {
            return Result.success(promotionService.active());
        }
    }
}