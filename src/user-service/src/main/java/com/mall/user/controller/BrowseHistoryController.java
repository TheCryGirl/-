package com.mall.user.controller;

import com.mall.user.common.Result;
import com.mall.user.dto.request.BrowseHistoryRequest;
import com.mall.user.dto.response.BrowseHistoryResponse;
import com.mall.user.service.BrowseHistoryService;
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

import java.util.List;

/**
 * 商品浏览足迹接口（#52）。
 */
@RestController
@RequestMapping("/history")
@RequiredArgsConstructor
public class BrowseHistoryController {

    private final BrowseHistoryService browseHistoryService;

    /** 记录一次浏览（同一商品去重，只更新时间） */
    @PostMapping
    public Result<Void> record(
            @RequestHeader("X-User-Id") String userId,
            @Valid @RequestBody BrowseHistoryRequest request) {
        browseHistoryService.record(userId, request.getProductId());
        return Result.success(null);
    }

    /** 最近 N 条足迹，按时间倒序 */
    @GetMapping
    public Result<List<BrowseHistoryResponse>> recent(
            @RequestHeader("X-User-Id") String userId,
            @RequestParam(defaultValue = "20") int limit) {
        return Result.success(browseHistoryService.recent(userId, limit));
    }

    @DeleteMapping("/{productId}")
    public Result<Void> remove(
            @RequestHeader("X-User-Id") String userId,
            @PathVariable("productId") String productId) {
        browseHistoryService.remove(userId, productId);
        return Result.success(null);
    }

    @DeleteMapping
    public Result<Void> clear(@RequestHeader("X-User-Id") String userId) {
        browseHistoryService.clear(userId);
        return Result.success(null);
    }
}
