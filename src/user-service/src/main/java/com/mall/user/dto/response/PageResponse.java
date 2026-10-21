package com.mall.user.dto.response;

import lombok.Data;

import java.util.List;

/**
 * 分页响应结构，与《05-接口契约》1.6 节保持一致。
 */
@Data
public class PageResponse<T> {

    private int page;
    private int size;
    private long total;
    private int pages;
    private List<T> list;

    public static <T> PageResponse<T> of(int page, int size, long total, List<T> list) {
        PageResponse<T> response = new PageResponse<>();
        response.setPage(page);
        response.setSize(size);
        response.setTotal(total);
        response.setPages((int) Math.ceil((double) total / size));
        response.setList(list);
        return response;
    }
}
