package com.mall.order.mapper;

import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 购买行为查询（#141）：给个性化推荐提供「最近买过什么」。
 *
 * <p>刻意独立成 mapper，不动既有 OrderMapper，避免与多店铺结算等并行改动冲突。
 */
public interface PurchaseHistoryMapper {

    /**
     * 最近购买过的商品ID，按订单创建时间倒序去重。
     *
     * <p>只统计已支付之后的状态：PENDING_PAYMENT 与 CANCELLED 不算购买行为。
     */
    List<String> selectRecentPurchasedProductIds(@Param("buyerId") String buyerId,
                                                 @Param("limit") int limit);
}
