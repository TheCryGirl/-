package com.mall.product.search;

import com.mall.product.dto.response.ProductSearchRow;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * 可解释的检索评分与纠错候选（#141）。纯计算，不依赖 Spring，方便单测。
 *
 * <p>评分口径（写进《05-接口契约》12.1）：
 * <pre>
 * score = 标题命中词数 × 3 + 副标题命中词数 × 2 + 分类名命中词数 × 1 + SKU 规格名命中词数 × 1
 * </pre>
 * 命中词数按「不同的检索词」计数，同一个词在一段文案里重复出现只算一次。
 */
public final class ProductSearchEngine {

    private static final int TITLE_WEIGHT = 3;
    private static final int SUBTITLE_WEIGHT = 2;
    private static final int CATEGORY_WEIGHT = 1;
    private static final int SPEC_WEIGHT = 1;
    /** 纠错候选最多返回几个。 */
    private static final int MAX_SUGGESTIONS = 2;

    private ProductSearchEngine() {
    }

    /** 一条命中的商品与它的相关度得分。 */
    public record Hit(ProductSearchRow row, int score) {
    }

    /**
     * 打分并按「得分 → 指定排序 → 商品ID」排序。
     *
     * @param rows      候选商品（已按分类 / 价格 / 库存筛过）
     * @param specNames productId → 规格名列表
     * @param tokens    检索词
     * @param sort      排序方式，与商品列表一致
     */
    public static List<Hit> rank(List<ProductSearchRow> rows,
                                 Map<String, List<String>> specNames,
                                 List<String> tokens,
                                 String sort) {
        if (rows == null || rows.isEmpty() || tokens == null || tokens.isEmpty()) {
            return List.of();
        }
        List<Hit> hits = new ArrayList<>();
        for (ProductSearchRow row : rows) {
            int score = score(row, specNames.getOrDefault(row.getProductId(), List.of()), tokens);
            if (score > 0) {
                hits.add(new Hit(row, score));
            }
        }
        hits.sort(comparator(sort));
        return hits;
    }

    /** 无关键词时（只按分类 / 价格 / 库存筛选）按排序规则返回全部候选。 */
    public static List<Hit> rankAll(List<ProductSearchRow> rows, String sort) {
        if (rows == null || rows.isEmpty()) {
            return List.of();
        }
        List<Hit> hits = new ArrayList<>(rows.size());
        for (ProductSearchRow row : rows) {
            hits.add(new Hit(row, 0));
        }
        hits.sort(comparator(sort));
        return hits;
    }

    /** 单条商品的评分。 */
    public static int score(ProductSearchRow row, List<String> specs, List<String> tokens) {        String title = lower(row.getTitle());
        String subtitle = lower(row.getSubtitle());
        String category = lower(row.getCategoryName());
        StringBuilder specText = new StringBuilder();
        if (specs != null) {
            for (String spec : specs) {
                specText.append(lower(spec)).append('\u0000');
            }
        }
        String specsJoined = specText.toString();

        int titleHits = 0;
        int subtitleHits = 0;
        int categoryHits = 0;
        int specHits = 0;
        for (String token : tokens) {
            if (KeywordAnalyzer.containsToken(title, token)) {
                titleHits++;
            }
            if (KeywordAnalyzer.containsToken(subtitle, token)) {
                subtitleHits++;
            }
            if (KeywordAnalyzer.containsToken(category, token)) {
                categoryHits++;
            }
            if (KeywordAnalyzer.containsToken(specsJoined, token)) {
                specHits++;
            }
        }
        return TITLE_WEIGHT * titleHits + SUBTITLE_WEIGHT * subtitleHits
                + CATEGORY_WEIGHT * categoryHits + SPEC_WEIGHT * specHits;
    }

    /**
     * 纠错候选：在商品文案里找与查询「只差一个字符」的片段。
     *
     * <p>只做提示，不改写查询：调用方仍用原关键词检索。
     *
     * @return 候选词，最多 {@value #MAX_SUGGESTIONS} 个
     */
    public static List<String> suggest(String keyword,
                                       List<ProductSearchRow> rows,
                                       Map<String, List<String>> specNames) {
        String query = KeywordAnalyzer.compact(keyword);
        if (query.length() < 2 || rows == null || rows.isEmpty()) {
            return List.of();
        }

        Map<String, Integer> candidates = new LinkedHashMap<>();
        for (ProductSearchRow row : rows) {
            if (collectFromText(lower(row.getTitle()), query, candidates)) {
                // 文案里已经出现原查询，说明用户没打错字，不提示
                return List.of();
            }
            collectFromText(lower(row.getSubtitle()), query, candidates);
            collectFromText(lower(row.getCategoryName()), query, candidates);
            for (String spec : specNames.getOrDefault(row.getProductId(), List.of())) {
                collectFromText(lower(spec), query, candidates);
            }
        }
        List<String> suggestions = new ArrayList<>();
        candidates.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                .limit(MAX_SUGGESTIONS)
                .forEach(entry -> suggestions.add(entry.getKey()));
        return suggestions;
    }

    /**
     * 在单段文案里滑动等长窗口找「差一个字符」的片段。
     *
     * @return true 表示文案里直接包含原查询（无需纠错）
     */
    private static boolean collectFromText(String text, String query, Map<String, Integer> out) {
        if (text == null || text.isBlank()) {
            return false;
        }
        if (text.contains(query)) {
            return true;
        }
        int length = query.length();
        Set<String> seen = new LinkedHashSet<>();
        for (int i = 0; i + length <= text.length(); i++) {
            String window = text.substring(i, i + length);
            if (window.equals(query) || !seen.add(window)) {
                continue;
            }
            if (KeywordAnalyzer.editDistance(query, window) <= 1) {
                out.merge(window, 1, Integer::sum);
            }
        }
        return false;
    }

    private static Comparator<Hit> comparator(String sort) {
        Comparator<Hit> byScore = Comparator.comparingInt(Hit::score).reversed();
        Comparator<Hit> secondary = switch (sort == null ? "default" : sort) {
            case "price_asc" -> Comparator.comparing(hit -> nullSafe(hit.row().getPrice()),
                    Comparator.nullsLast(Comparator.naturalOrder()));
            case "price_desc" -> Comparator.comparing((Hit hit) -> nullSafe(hit.row().getPrice()),
                    Comparator.nullsLast(Comparator.reverseOrder()));
            case "sales_desc" -> Comparator.comparing(hit -> nullSafe(hit.row().getSalesCount()),
                    Comparator.nullsLast(Comparator.reverseOrder()));
            default -> Comparator.comparing((Hit hit) -> time(hit.row().getCreatedAt()),
                    Comparator.nullsLast(Comparator.reverseOrder()));
        };
        // 商品ID兜底，保证同样得分时翻页顺序稳定
        return byScore.thenComparing(secondary)
                .thenComparing(hit -> hit.row().getProductId() == null ? "" : hit.row().getProductId());
    }

    private static Long nullSafe(Long value) {
        return value == null ? 0L : value;
    }

    private static LocalDateTime time(LocalDateTime value) {
        return value == null ? LocalDateTime.MIN : value;
    }

    private static String lower(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT);
    }
}
