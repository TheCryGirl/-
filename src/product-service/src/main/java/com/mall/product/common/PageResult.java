package com.mall.product.common;

import java.util.List;

/**
 * 契约分页结构：page / size / total / pages / list。
 */
public record PageResult<T>(int page, int size, long total, int pages, List<T> list) {

    public static <T> PageResult<T> of(List<T> list, long total, int page, int size) {
        int safeSize = Math.max(1, size);
        int pages = (int) ((total + safeSize - 1) / safeSize);
        return new PageResult<>(page, safeSize, total, pages, list);
    }
}
