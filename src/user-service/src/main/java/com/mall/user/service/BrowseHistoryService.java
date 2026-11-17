package com.mall.user.service;

import com.mall.user.dto.response.BrowseHistoryResponse;

import java.util.List;

/**
 * 浏览足迹服务。
 */
public interface BrowseHistoryService {

    /** 记录一次浏览，同一商品只更新时间（去重） */
    void record(String userId, String productId);

    /** 最近 N 条足迹，按浏览时间倒序 */
    List<BrowseHistoryResponse> recent(String userId, int limit);

    /**
     * 最近浏览过的商品ID（#141：供 product-service 计算个性化推荐）。
     *
     * <p>只返回ID，不做商品信息拼装，避免推荐链路为了推荐理由多打一次商品接口。
     */
    List<String> recentProductIds(String userId, int limit);

    /** 删除单条足迹 */
    void remove(String userId, String productId);

    /** 清空足迹 */
    void clear(String userId);
}
