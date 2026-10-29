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

    /** 删除单条足迹 */
    void remove(String userId, String productId);

    /** 清空足迹 */
    void clear(String userId);
}
