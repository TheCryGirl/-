package com.mall.user.service.impl;

import com.mall.user.client.dto.ProductBriefDTO;
import com.mall.user.common.BizException;
import com.mall.user.common.ErrorCode;
import com.mall.user.common.IdGenerator;
import com.mall.user.dto.response.BrowseHistoryResponse;
import com.mall.user.entity.BrowseHistory;
import com.mall.user.mapper.BrowseHistoryMapper;
import com.mall.user.service.BrowseHistoryService;
import com.mall.user.service.ProductInfoService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 浏览足迹实现。
 *
 * <p>浏览同一商品不新增记录，只更新 visited_at，天然完成去重；
 * 列表按 visited_at 倒序取最近 N 条。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BrowseHistoryServiceImpl implements BrowseHistoryService {

    private static final int DEFAULT_LIMIT = 20;
    private static final int MAX_LIMIT = 50;

    private final BrowseHistoryMapper browseHistoryMapper;
    private final ProductInfoService productInfoService;

    @Override
    public void record(String userId, String productId) {
        requireUser(userId);
        String pid = requireProductId(productId);
        LocalDateTime now = LocalDateTime.now();

        int updated = browseHistoryMapper.updateVisitedAt(userId, pid, now);
        if (updated > 0) {
            return;
        }
        BrowseHistory history = new BrowseHistory();
        history.setHistoryId(IdGenerator.historyId());
        history.setUserId(userId);
        history.setProductId(pid);
        history.setVisitedAt(now);
        try {
            browseHistoryMapper.insert(history);
        } catch (DuplicateKeyException e) {
            // 并发首次浏览：唯一索引挡住后改成更新时间
            browseHistoryMapper.updateVisitedAt(userId, pid, now);
        }
    }

    @Override
    public List<BrowseHistoryResponse> recent(String userId, int limit) {
        requireUser(userId);
        int safeLimit = limit <= 0 ? DEFAULT_LIMIT : Math.min(MAX_LIMIT, limit);
        List<BrowseHistory> histories = browseHistoryMapper.selectRecent(userId, safeLimit);
        if (histories.isEmpty()) {
            return List.of();
        }

        List<String> productIds = histories.stream().map(BrowseHistory::getProductId).toList();
        Map<String, ProductBriefDTO> products = productInfoService.load(productIds);
        List<BrowseHistoryResponse> list = new ArrayList<>(histories.size());
        for (BrowseHistory history : histories) {
            ProductBriefDTO product = products.get(history.getProductId());
            String reason = ProductInfoService.invalidReason(product);
            BrowseHistoryResponse response = new BrowseHistoryResponse();
            response.setHistoryId(history.getHistoryId());
            response.setProductId(history.getProductId());
            response.setTitle(product == null ? "商品已删除" : product.getTitle());
            response.setMainImage(product == null ? null : product.getMainImage());
            response.setPrice(product == null || product.getPrice() == null ? 0L : product.getPrice());
            response.setStatus(product == null ? null : product.getStatus());
            response.setInvalid(reason != null);
            response.setInvalidReason(reason);
            response.setVisitedAt(history.getVisitedAt());
            list.add(response);
        }
        return list;
    }

    @Override
    public void remove(String userId, String productId) {
        requireUser(userId);
        browseHistoryMapper.deleteByUserAndProduct(userId, requireProductId(productId));
    }

    @Override
    public void clear(String userId) {
        requireUser(userId);
        browseHistoryMapper.deleteAllByUserId(userId);
    }

    private static void requireUser(String userId) {
        if (userId == null || userId.isBlank()) {
            throw new BizException(ErrorCode.UNAUTHORIZED, "未登录或用户信息缺失");
        }
    }

    private static String requireProductId(String productId) {
        if (productId == null || productId.isBlank()) {
            throw new BizException(ErrorCode.PARAM_INVALID, "商品ID不能为空");
        }
        return productId.trim();
    }
}
