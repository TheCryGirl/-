package com.mall.order.controller;

import com.mall.order.common.Result;
import com.mall.order.config.InternalTokenVerifier;
import com.mall.order.service.PurchaseHistoryService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 内部接口：最近购买过的商品ID（#141）。
 *
 * <p>只有 product-service 的推荐链路用它，必须带内部令牌。
 */
@RestController
@RequiredArgsConstructor
public class InternalPurchaseController {

    private final PurchaseHistoryService purchaseHistoryService;
    private final InternalTokenVerifier internalTokenVerifier;

    @GetMapping("/internal/purchases/{userId}")
    public Result<List<String>> recentPurchases(
            @RequestHeader(value = "X-Internal-Token", required = false) String token,
            @PathVariable("userId") String userId,
            @RequestParam(defaultValue = "20") int limit) {
        internalTokenVerifier.verify(token);
        return Result.success(purchaseHistoryService.recentPurchasedProductIds(userId, limit));
    }
}
