package com.mall.payment.common;

import lombok.Data;

import java.util.List;

/**
 * 分页返回结构，与《05-接口契约》1.6 节一致。
 */
@Data
public class PageResult<T> {

    private int page;
    private int size;
    private long total;
    private int pages;
    private List<T> list;

    public static <T> PageResult<T> of(List<T> list, long total, int page, int size) {
        PageResult<T> result = new PageResult<>();
        result.setPage(page);
        result.setSize(size);
        result.setTotal(total);
        result.setPages(size <= 0 ? 0 : (int) ((total + size - 1) / size));
        result.setList(list);
        return result;
    }
}
