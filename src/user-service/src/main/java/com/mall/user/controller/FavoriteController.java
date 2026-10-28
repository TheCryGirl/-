package com.mall.user.controller;

import com.mall.user.common.Result;
import com.mall.user.dto.request.FavoriteRequest;
import com.mall.user.dto.response.FavoriteResponse;
import com.mall.user.dto.response.PageResponse;
import com.mall.user.service.FavoriteService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 商品收藏接口（#52）。
 */
@RestController
@RequestMapping("/favorites")
@RequiredArgsConstructor
public class FavoriteController {

    private final FavoriteService favoriteService;

    /** 收藏商品，重复收藏幂等返回同一记录 */
    @PostMapping
    public Result<FavoriteResponse> add(
            @RequestHeader("X-User-Id") String userId,
            @Valid @RequestBody FavoriteRequest request) {
        return Result.success(favoriteService.add(userId, request.getProductId()));
    }

    /** 取消收藏 */
    @DeleteMapping("/{productId}")
    public Result<Void> remove(
            @RequestHeader("X-User-Id") String userId,
            @PathVariable("productId") String productId) {
        favoriteService.remove(userId, productId);
        return Result.success(null);
    }

    /** 收藏夹分页展示 */
    @GetMapping
    public Result<PageResponse<FavoriteResponse>> list(
            @RequestHeader("X-User-Id") String userId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        return Result.success(favoriteService.list(userId, page, size));
    }

    /** 商品详情页判断当前用户是否已收藏 */
    @GetMapping("/status")
    public Result<Map<String, Object>> status(
            @RequestHeader("X-User-Id") String userId,
            @RequestParam("productId") String productId) {
        return Result.success(Map.of(
                "productId", productId,
                "favorited", favoriteService.isFavorited(userId, productId)));
    }
}
