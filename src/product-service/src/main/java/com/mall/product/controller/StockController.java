package com.mall.product.controller;

import com.mall.product.common.Result;
import com.mall.product.dto.request.ConfirmStockRequest;
import com.mall.product.dto.request.ReleaseStockRequest;
import com.mall.product.dto.request.ReserveStockRequest;
import com.mall.product.dto.response.ConfirmResponse;
import com.mall.product.dto.response.ReleaseResponse;
import com.mall.product.dto.response.ReserveStockResponse;
import com.mall.product.config.InternalTokenVerifier;
import com.mall.product.service.StockService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 库存内部接口，路径与《05-接口契约》3.5 节一致。
 */
@RestController
@RequestMapping({"/products/skus", "/api/products/skus"})
@RequiredArgsConstructor
public class StockController {

    private final StockService stockService;
    private final InternalTokenVerifier internalTokenVerifier;

    @PostMapping("/reserve")
    public Result<ReserveStockResponse> reserve(
            @RequestHeader(value = "X-Internal-Token", required = false) String token,
            @Valid @RequestBody ReserveStockRequest request) {
        internalTokenVerifier.verify(token);
        return Result.success(stockService.reserve(request));
    }

    @PostMapping("/release")
    public Result<ReleaseResponse> release(
            @RequestHeader(value = "X-Internal-Token", required = false) String token,
            @Valid @RequestBody ReleaseStockRequest request) {
        internalTokenVerifier.verify(token);
        return Result.success(stockService.release(request));
    }

    @PostMapping("/confirm")
    public Result<ConfirmResponse> confirm(
            @RequestHeader(value = "X-Internal-Token", required = false) String token,
            @Valid @RequestBody ConfirmStockRequest request) {
        internalTokenVerifier.verify(token);
        return Result.success(stockService.confirm(request));
    }

    /** 退款成功后回补库存（BR-28），由 payment-service 调用 */
    @PostMapping("/refund-restock")
    public Result<Void> refundRestock(
            @RequestHeader(value = "X-Internal-Token", required = false) String token,
            @Valid @RequestBody ConfirmStockRequest request) {
        internalTokenVerifier.verify(token);
        stockService.refundRestock(request.getOrderId());
        return Result.success(null);
    }
}
