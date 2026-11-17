package com.mall.user.controller;

import com.mall.user.common.Result;
import com.mall.user.service.BrowseHistoryService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 内部接口：最近浏览商品ID（#141）。
 *
 * <p>只给可信服务调用：ServiceIngressFilter 已对整个服务校验 X-Internal-Token，
 * 这里不再重复校验，避免同一请求两次验签。
 */
@RestController
@RequiredArgsConstructor
public class InternalBrowseHistoryController {

    private final BrowseHistoryService browseHistoryService;

    @GetMapping("/internal/browse-history/{userId}")
    public Result<List<String>> recentProductIds(
            @PathVariable("userId") String userId,
            @RequestParam(defaultValue = "20") int limit) {
        return Result.success(browseHistoryService.recentProductIds(userId, limit));
    }
}
