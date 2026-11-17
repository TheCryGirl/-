package com.mall.product.common;

import java.util.List;

/**
 * 契约分页结构：page / size / total / pages / list。
 *
 * <p>#141 起新增 {@code suggestions}：搜索关键词疑似拼写错误时返回纠错候选词（只提示，不改写查询）。
 * 其余列表接口沿用 {@link #of(List, long, int, int)}，该字段为空数组，既有语义不变。
 */
public record PageResult<T>(int page, int size, long total, int pages, List<T> list, List<String> suggestions) {

    public static <T> PageResult<T> of(List<T> list, long total, int page, int size) {
        return of(list, total, page, size, List.of());
    }

    public static <T> PageResult<T> of(List<T> list, long total, int page, int size, List<String> suggestions) {
        int safeSize = Math.max(1, size);
        int pages = (int) ((total + safeSize - 1) / safeSize);
        return new PageResult<>(page, safeSize, total, pages, list,
                suggestions == null ? List.of() : suggestions);
    }
}
