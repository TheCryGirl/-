package com.mall.product.search;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * 关键词分析：不做中文分词，只按字符做二元切分（#141）。
 *
 * <p>规则：
 * <ul>
 *   <li>先去首尾空白，ASCII 字母统一转小写，因此英文与数字大小写不敏感；</li>
 *   <li>按空白与标点把输入切成若干「段」；</li>
 *   <li>段内的连续中文按二元组切分（{@code 无线耳机} → {@code 无线 / 线耳 / 耳机}），单字保留单字；</li>
 *   <li>段内的连续西文 / 数字整体作为一个词（{@code iphone15}、{@code 5g}）。</li>
 * </ul>
 *
 * <p>不引分词库，保证实现可解释、可复现，评价的是「命中多少个二元组」。
 */
public final class KeywordAnalyzer {

    /** 单次查询最多分析的词数，防止超长输入拖慢检索。 */
    public static final int MAX_TOKENS = 24;

    private KeywordAnalyzer() {
    }

    /** 规整：去空白、ASCII 小写。 */
    public static String normalize(String raw) {
        if (raw == null) {
            return "";
        }
        return raw.trim().toLowerCase(Locale.ROOT);
    }

    /** 去掉所有空白，用于纠错比较（「无线 耳机」与「无线耳机」视为同一查询）。 */
    public static String compact(String raw) {
        return normalize(raw).replaceAll("\\s+", "");
    }

    /**
     * 把关键词切成检索词。
     *
     * @return 去重后的词表，最多 {@link #MAX_TOKENS} 个
     */
    public static List<String> tokens(String keyword) {
        String normalized = normalize(keyword);
        if (normalized.isEmpty()) {
            return List.of();
        }
        Set<String> tokens = new LinkedHashSet<>();
        for (String segment : segments(normalized)) {
            if (segment.isEmpty()) {
                continue;
            }
            if (containsCjk(segment)) {
                addCjkTokens(segment, tokens);
            } else {
                tokens.add(segment);
            }
            if (tokens.size() >= MAX_TOKENS) {
                break;
            }
        }
        List<String> result = new ArrayList<>(tokens);
        return result.size() > MAX_TOKENS ? result.subList(0, MAX_TOKENS) : result;
    }

    /** 按空白与标点切段。 */
    static List<String> segments(String normalized) {
        List<String> segments = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        for (int i = 0; i < normalized.length(); i++) {
            char c = normalized.charAt(i);
            if (Character.isLetterOrDigit(c)) {
                current.append(c);
            } else if (current.length() > 0) {
                segments.add(current.toString());
                current.setLength(0);
            }
        }
        if (current.length() > 0) {
            segments.add(current.toString());
        }
        return segments;
    }

    /**
     * 段内切词：连续中文按二元组，连续西文数字整体保留。
     */
    private static void addCjkTokens(String segment, Set<String> out) {
        int i = 0;
        while (i < segment.length()) {
            if (isCjk(segment.charAt(i))) {
                int start = i;
                while (i < segment.length() && isCjk(segment.charAt(i))) {
                    i++;
                }
                String run = segment.substring(start, i);
                if (run.length() == 1) {
                    out.add(run);
                } else {
                    for (int k = 0; k + 1 < run.length(); k++) {
                        out.add(run.substring(k, k + 2));
                    }
                }
            } else {
                int start = i;
                while (i < segment.length() && !isCjk(segment.charAt(i))) {
                    i++;
                }
                out.add(segment.substring(start, i));
            }
        }
    }

    public static boolean containsCjk(String text) {
        if (text == null) {
            return false;
        }
        for (int i = 0; i < text.length(); i++) {
            if (isCjk(text.charAt(i))) {
                return true;
            }
        }
        return false;
    }

    /** 中日韩统一表意文字及扩展 A 段，够覆盖本项目的商品文案。 */
    public static boolean isCjk(char c) {
        return (c >= 0x4E00 && c <= 0x9FFF) || (c >= 0x3400 && c <= 0x4DBF);
    }

    /** Levenshtein 编辑距离，用于「输错一个字」的纠错候选。 */
    public static int editDistance(String left, String right) {
        if (left == null || right == null) {
            return Integer.MAX_VALUE;
        }
        int n = left.length();
        int m = right.length();
        if (Math.abs(n - m) > 1) {
            // 纠错只处理差一个字符以内的输入，超出的直接判为不相似
            return Math.abs(n - m);
        }
        int[] previous = new int[m + 1];
        int[] current = new int[m + 1];
        for (int j = 0; j <= m; j++) {
            previous[j] = j;
        }
        for (int i = 1; i <= n; i++) {
            current[0] = i;
            for (int j = 1; j <= m; j++) {
                int cost = left.charAt(i - 1) == right.charAt(j - 1) ? 0 : 1;
                current[j] = Math.min(Math.min(current[j - 1] + 1, previous[j] + 1), previous[j - 1] + cost);
            }
            int[] swap = previous;
            previous = current;
            current = swap;
        }
        return previous[m];
    }

    /** 判断 text 是否包含某个检索词（调用方先把两边都规整为小写）。 */
    public static boolean containsToken(String text, String token) {
        return text != null && !token.isEmpty() && text.contains(token);
    }
}
