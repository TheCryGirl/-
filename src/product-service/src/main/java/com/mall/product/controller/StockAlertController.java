package com.mall.product.controller;

import com.mall.product.common.BizException;
import com.mall.product.common.ErrorCode;
import com.mall.product.common.Result;
import com.mall.product.dto.request.UpdateLowStockThresholdRequest;
import com.mall.product.dto.response.LowStockAlertResponse;
import com.mall.product.service.StockAlertService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping({"/products", "/api/products"})
@RequiredArgsConstructor
public class StockAlertController {

    private final StockAlertService stockAlertService;

    @GetMapping("/low-stock-threshold")
    public Result<Integer> globalThreshold(
            @RequestHeader(value = "X-User-Role", required = false) String role) {
        requireAdmin(role);
        return Result.success(stockAlertService.globalThreshold());
    }

    @PutMapping("/low-stock-threshold")
    public Result<Integer> updateGlobalThreshold(
            @RequestHeader(value = "X-User-Role", required = false) String role,
            @Valid @RequestBody UpdateLowStockThresholdRequest request) {
        requireAdmin(role);
        return Result.success(stockAlertService.updateGlobalThreshold(request.getThreshold()));
    }

    @PutMapping("/skus/{skuId}/low-stock-threshold")
    public Result<Integer> updateSkuThreshold(
            @PathVariable String skuId,
            @RequestHeader(value = "X-User-Id", required = false) String sellerId,
            @RequestHeader(value = "X-User-Role", required = false) String role,
            @Valid @RequestBody UpdateLowStockThresholdRequest request) {
        boolean admin = "ADMIN".equalsIgnoreCase(role);
        if (!admin && (sellerId == null || sellerId.isBlank())) {
            throw new BizException(ErrorCode.UNAUTHORIZED, "未登录或用户信息缺失");
        }
        return Result.success(stockAlertService.updateSkuThreshold(
                skuId, admin ? null : sellerId, request.getThreshold()));
    }

    @GetMapping("/skus/low-stock-alerts")
    public Result<List<LowStockAlertResponse>> alerts(
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @RequestHeader(value = "X-User-Role", required = false) String role) {
        return Result.success(stockAlertService.alerts(userId, "ADMIN".equalsIgnoreCase(role)));
    }

    private static void requireAdmin(String role) {
        if (!"ADMIN".equalsIgnoreCase(role)) {
            throw new BizException(ErrorCode.FORBIDDEN, "仅管理员可修改全局库存阈值");
        }
    }
}

