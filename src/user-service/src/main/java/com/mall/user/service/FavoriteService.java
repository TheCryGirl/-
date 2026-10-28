package com.mall.user.service;

import com.mall.user.dto.response.FavoriteResponse;
import com.mall.user.dto.response.PageResponse;

/**
 * 收藏夹服务。
 */
public interface FavoriteService {

    /** 收藏商品，重复收藏返回已有记录（幂等） */
    FavoriteResponse add(String userId, String productId);

    /** 取消收藏，未收藏时也返回成功（幂等） */
    void remove(String userId, String productId);

    /** 分页展示收藏夹，商品信息实时取自 product-service */
    PageResponse<FavoriteResponse> list(String userId, int page, int size);

    /** 商品详情页判断是否已收藏 */
    boolean isFavorited(String userId, String productId);
}
