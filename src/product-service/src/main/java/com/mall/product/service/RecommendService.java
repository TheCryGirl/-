package com.mall.product.service;

import com.mall.product.client.PurchaseHistoryClient;
import com.mall.product.client.UserBehaviorClient;
import com.mall.product.common.Result;
import com.mall.product.dto.response.ProductBriefResponse;
import com.mall.product.dto.response.ProductSearchRow;
import com.mall.product.dto.response.RecommendResponse;
import com.mall.product.entity.Category;
import com.mall.product.entity.Product;
import com.mall.product.mapper.CategoryMapper;
import com.mall.product.mapper.ProductMapper;
import com.mall.product.search.SearchRowMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * 个性化推荐（#141）：按行为归纳分类 → 该分类下按销量取商品 → 不足补全站热销。
 *
 * <p>策略刻意保持可解释，不做模型：
 * <ol>
 *   <li>最近浏览（user-service）每个商品计 1 分，最近购买（order-service）每个计 3 分；</li>
 *   <li>按分类累加得分，取前 3 个分类作为推荐池；详情页场景优先当前商品所属分类；</li>
 *   <li>池内按 {@code sales_count} 降序取商品，排除已购买、已下架、库存为 0；</li>
 *   <li>不足 limit 时用全站热销补齐，保证新用户（无行为）也有结果。</li>
 * </ol>
 *
 * <p>行为接口不可用时按冷启动处理：只记 warn，不让推荐整体失败。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RecommendService {

    private static final int DEFAULT_LIMIT = 8;
    private static final int MAX_LIMIT = 20;
    private static final int BEHAVIOR_LIMIT = 20;
    private static final int TOP_CATEGORIES = 3;
    private static final int BROWSE_WEIGHT = 1;
    private static final int PURCHASE_WEIGHT = 3;
    private static final String SCENE_DETAIL = "DETAIL";
    private static final String SCENE_HOME = "HOME";

    private final ProductMapper productMapper;
    private final CategoryMapper categoryMapper;
    private final UserBehaviorClient userBehaviorClient;
    private final PurchaseHistoryClient purchaseHistoryClient;

    public RecommendResponse recommend(String scene, String productId, String userId, int limit) {
        int safeLimit = limit <= 0 ? DEFAULT_LIMIT : Math.min(MAX_LIMIT, limit);
        String safeScene = SCENE_DETAIL.equalsIgnoreCase(scene == null ? "" : scene.trim())
                ? SCENE_DETAIL : SCENE_HOME;

        Product baseProduct = safeScene.equals(SCENE_DETAIL) ? findProduct(productId) : null;

        List<String> browseIds = safeLoad(() -> userBehaviorClient.recentBrowseHistory(userId, BEHAVIOR_LIMIT),
                "浏览行为", userId);
        List<String> purchaseIds = safeLoad(() -> purchaseHistoryClient.recentPurchases(userId, BEHAVIOR_LIMIT),
                "购买行为", userId);
        boolean coldStart = browseIds.isEmpty() && purchaseIds.isEmpty();

        Set<String> excludeIds = new LinkedHashSet<>(purchaseIds);
        if (baseProduct != null) {
            excludeIds.add(baseProduct.getProductId());
        }

        List<String> categoryIds = topCategories(categoryScores(browseIds, purchaseIds), baseProduct);

        List<ProductSearchRow> picked = new ArrayList<>();
        if (!categoryIds.isEmpty()) {
            picked.addAll(dedupe(productMapper.selectRecommendRows(categoryIds,
                    new ArrayList<>(excludeIds), safeLimit * 4), safeLimit));
            for (ProductSearchRow row : picked) {
                excludeIds.add(row.getProductId());
            }
        }
        if (picked.size() < safeLimit) {
            // 冷启动或同类商品不足：用全站热销补齐，保证不为空
            picked.addAll(dedupe(productMapper.selectHotRows(new ArrayList<>(excludeIds),
                    (safeLimit - picked.size()) * 4), safeLimit - picked.size()));
        }

        RecommendResponse response = new RecommendResponse();
        response.setScene(safeScene);
        response.setColdStart(coldStart);
        response.setBasis(basisOf(categoryIds, categoryNames(categoryIds, picked, baseProduct), coldStart));
        response.setItems(picked.stream().map(SearchRowMapper::toListItem).toList());
        return response;
    }

    /** 取行为数据：任何异常都降级为空，保证推荐接口本身可用。 */
    private List<String> safeLoad(Supplier<Result<List<String>>> call, String what, String userId) {
        if (userId == null || userId.isBlank()) {
            return List.of();
        }
        try {
            Result<List<String>> result = call.get();
            if (result == null || result.getCode() != 0 || result.getData() == null) {
                return List.of();
            }
            return result.getData().stream().filter(id -> id != null && !id.isBlank()).distinct().toList();
        } catch (Exception e) {
            log.warn("读取{}失败，按无行为处理, userId={}", what, userId, e);
            return List.of();
        }
    }

    /**
     * 分类得分：浏览计 1 分、购买计 3 分；商品分类通过本地商品表反查，不跨库 JOIN。
     */
    private Map<String, Integer> categoryScores(List<String> browseIds, List<String> purchaseIds) {
        Set<String> allIds = new LinkedHashSet<>(browseIds);
        allIds.addAll(purchaseIds);
        Map<String, String> categoryOf = categoryOfProduct(allIds);

        Map<String, Integer> scores = new LinkedHashMap<>();
        for (String productId : browseIds) {
            String categoryId = categoryOf.get(productId);
            if (categoryId != null) {
                scores.merge(categoryId, BROWSE_WEIGHT, Integer::sum);
            }
        }
        for (String productId : purchaseIds) {
            String categoryId = categoryOf.get(productId);
            if (categoryId != null) {
                scores.merge(categoryId, PURCHASE_WEIGHT, Integer::sum);
            }
        }
        return scores;
    }

    private Map<String, String> categoryOfProduct(Set<String> productIds) {
        Map<String, String> categoryOf = new LinkedHashMap<>();
        if (productIds.isEmpty()) {
            return categoryOf;
        }
        for (ProductBriefResponse brief : productMapper.selectBriefByIds(new ArrayList<>(productIds))) {
            if (brief.getCategoryId() != null) {
                categoryOf.put(brief.getProductId(), brief.getCategoryId());
            }
        }
        return categoryOf;
    }

    private List<String> topCategories(Map<String, Integer> scores, Product baseProduct) {
        List<String> categories = scores.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue(Comparator.reverseOrder()))
                .limit(TOP_CATEGORIES)
                .map(Map.Entry::getKey)
                .collect(Collectors.toCollection(ArrayList::new));
        if (baseProduct != null && baseProduct.getCategoryId() != null) {
            // 详情页先看同类商品，行为分类作为补充
            categories.remove(baseProduct.getCategoryId());
            categories.add(0, baseProduct.getCategoryId());
        }
        return categories.stream().distinct().limit(TOP_CATEGORIES).toList();
    }

    /** 推荐依据的分类名：优先用结果行里的分类名，详情页补上当前商品的分类。 */
    private Map<String, String> categoryNames(List<String> categoryIds, List<ProductSearchRow> picked,
                                              Product baseProduct) {
        Map<String, String> names = new LinkedHashMap<>();
        for (ProductSearchRow row : picked) {
            if (row.getCategoryId() != null && row.getCategoryName() != null) {
                names.putIfAbsent(row.getCategoryId(), row.getCategoryName());
            }
        }
        if (baseProduct != null && baseProduct.getCategoryId() != null) {
            Category category = categoryMapper.selectById(baseProduct.getCategoryId());
            if (category != null && category.getName() != null) {
                names.putIfAbsent(baseProduct.getCategoryId(), category.getName());
            }
        }
        return names;
    }

    private static List<String> basisOf(List<String> categoryIds, Map<String, String> categoryNames,
                                        boolean coldStart) {
        if (coldStart) {
            return List.of("全站热销");
        }
        List<String> basis = new ArrayList<>();
        for (String categoryId : categoryIds) {
            String name = categoryNames.get(categoryId);
            if (name != null && !basis.contains(name)) {
                basis.add(name);
            }
        }
        return basis.isEmpty() ? List.of("全站热销") : basis;
    }

    private static List<ProductSearchRow> dedupe(List<ProductSearchRow> rows, int limit) {
        Map<String, ProductSearchRow> unique = new LinkedHashMap<>();
        for (ProductSearchRow row : rows) {
            if (row.getProductId() != null) {
                unique.putIfAbsent(row.getProductId(), row);
            }
            if (unique.size() >= limit) {
                break;
            }
        }
        return new ArrayList<>(unique.values());
    }

    private Product findProduct(String productId) {
        if (productId == null || productId.isBlank()) {
            return null;
        }
        return productMapper.selectById(productId.trim());
    }
}
