package com.mall.product.client;

import com.mall.product.common.Result;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

/**
 * 用户行为客户端（#141）：读最近浏览足迹，用于个性化推荐。
 *
 * <p>这是内部接口，X-Internal-Token 由 TraceFeignConfig 统一注入；
 * 调用失败只降级为「无行为」，不阻塞推荐。
 */
@FeignClient(name = "user-service", url = "${services.user-url:}", contextId = "userBehaviorClient")
public interface UserBehaviorClient {

    /** 最近浏览过的商品ID，按浏览时间倒序 */
    @GetMapping("/internal/browse-history/{userId}")
    Result<List<String>> recentBrowseHistory(@PathVariable("userId") String userId,
                                             @RequestParam("limit") int limit);
}
