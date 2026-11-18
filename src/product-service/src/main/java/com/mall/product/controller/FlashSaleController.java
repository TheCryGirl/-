package com.mall.product.controller;

import com.mall.product.common.Result;
import com.mall.product.dto.request.FlashSaleReserveRequest;
import com.mall.product.dto.response.FlashSaleReserveResponse;
import com.mall.product.dto.response.FlashSaleResponse;
import com.mall.product.service.FlashSaleService;
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

/**
 * 限时秒杀接口（#140）。列表公开（首页秒杀位），下单需登录。
 */
@RestController
@RequestMapping({"/products/flash-sales", "/api/products/flash-sales"})
@RequiredArgsConstructor
public class FlashSaleController {

    private final FlashSaleService flashSaleService;

    /** 进行中的秒杀活动，含倒计时结束时间与剩余库存 */
    @GetMapping
    public Result<List<FlashSaleResponse>> ongoing() {
        return Result.success(flashSaleService.ongoing());
    }

    /** 秒杀下单：条件 UPDATE 扣库存，限购靠唯一键 */
    @PostMapping("/{saleId}/reserve")
    public Result<FlashSaleReserveResponse> reserve(
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @PathVariable("saleId") String saleId,
            @Valid @RequestBody FlashSaleReserveRequest request) {
        if (userId == null || userId.isBlank()) {
            throw new com.mall.product.common.BizException(
                    com.mall.product.common.ErrorCode.UNAUTHORIZED, "未登录或用户信息缺失");
        }
        return Result.success(flashSaleService.reserve(userId, saleId, request.getQuantity()));
    }
}
