package com.mall.order.service;

import com.mall.order.mapper.PurchaseHistoryMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 购买行为读取（#141）。只读，无事务。
 */
@Service
@RequiredArgsConstructor
public class PurchaseHistoryService {

    private static final int DEFAULT_LIMIT = 20;
    private static final int MAX_LIMIT = 50;

    private final PurchaseHistoryMapper purchaseHistoryMapper;

    public List<String> recentPurchasedProductIds(String buyerId, int limit) {
        if (buyerId == null || buyerId.isBlank()) {
            return List.of();
        }
        int safeLimit = limit <= 0 ? DEFAULT_LIMIT : Math.min(MAX_LIMIT, limit);
        return purchaseHistoryMapper.selectRecentPurchasedProductIds(buyerId.trim(), safeLimit);
    }
}
