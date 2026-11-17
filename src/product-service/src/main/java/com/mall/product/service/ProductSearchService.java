package com.mall.product.service;

import com.mall.product.common.PageResult;
import com.mall.product.dto.response.ProductListItem;
import com.mall.product.dto.response.ProductSearchRow;
import com.mall.product.dto.response.ProductSpecRow;
import com.mall.product.mapper.ProductMapper;
import com.mall.product.search.KeywordAnalyzer;
import com.mall.product.search.ProductSearchEngine;
import com.mall.product.search.SearchRowMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 商品检索（#141）：二元分词 + 多字段匹配 + 可解释评分 + 纠错候选。
 *
 * <p>不引分词库也不上 Elasticsearch：候选集由 SQL 按可见性与属性筛出，
 * 关键词匹配与排序在 Java 侧完成，1000 条商品量级下是一次全量打分。
 */
@Service
@RequiredArgsConstructor
public class ProductSearchService {

    private final ProductMapper productMapper;

    /**
     * 公开检索。
     *
     * @param inStock true 只看有货 / false 只看无货 / null 沿用默认（只看有货）
     */
    public PageResult<ProductListItem> search(String keyword, String categoryId, Long minPrice, Long maxPrice,
                                              Boolean inStock, String sort, int page, int size) {
        String trimmedKeyword = trimToNull(keyword);
        List<ProductSearchRow> rows = productMapper.selectSearchRows(trimToNull(categoryId), minPrice, maxPrice,
                stockScope(inStock));
        Map<String, List<String>> specNames = specIndex();
        List<String> tokens = KeywordAnalyzer.tokens(trimmedKeyword);

        List<ProductSearchEngine.Hit> hits = tokens.isEmpty()
                ? ProductSearchEngine.rankAll(rows, sort)
                : ProductSearchEngine.rank(rows, specNames, tokens, sort);

        int safePage = Math.max(1, page);
        int safeSize = Math.min(100, Math.max(1, size));
        int offset = (safePage - 1) * safeSize;
        List<ProductListItem> items = hits.stream()
                .skip(offset)
                .limit(safeSize)
                .map(hit -> SearchRowMapper.toListItem(hit.row()))
                .toList();
        List<String> suggestions = trimmedKeyword == null
                ? List.of()
                : verifiedSuggestions(trimmedKeyword, rows, specNames);
        return PageResult.of(items, hits.size(), safePage, safeSize, suggestions);
    }

    /** 纠错候选只保留「真的能搜出商品」的词，避免提示了却搜不到。 */
    private List<String> verifiedSuggestions(String keyword, List<ProductSearchRow> rows,
                                             Map<String, List<String>> specNames) {
        List<String> candidates = ProductSearchEngine.suggest(keyword, rows, specNames);
        if (candidates.isEmpty()) {
            return List.of();
        }
        List<String> verified = new ArrayList<>();
        for (String candidate : candidates) {
            List<String> tokens = KeywordAnalyzer.tokens(candidate);
            if (!tokens.isEmpty() && !ProductSearchEngine.rank(rows, specNames, tokens, "default").isEmpty()) {
                verified.add(candidate);
            }
        }
        return verified;
    }

    /** productId → 规格名列表。 */
    private Map<String, List<String>> specIndex() {
        Map<String, List<String>> index = new LinkedHashMap<>();
        for (ProductSpecRow row : productMapper.selectSpecNames()) {
            index.computeIfAbsent(row.getProductId(), key -> new ArrayList<>()).add(row.getSpecName());
        }
        return index;
    }

    private static String stockScope(Boolean inStock) {
        if (inStock == null) {
            return "IN";
        }
        return inStock ? "IN" : "OUT";
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
