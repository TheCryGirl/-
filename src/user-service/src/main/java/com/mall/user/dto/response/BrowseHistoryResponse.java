package com.mall.user.dto.response;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 浏览足迹条目，字段含义与收藏夹一致，时间取最近一次浏览时间。
 */
@Data
public class BrowseHistoryResponse {

    private String historyId;
    private String productId;
    private String title;
    private String mainImage;
    private Long price;
    private String status;
    private Boolean invalid;
    private String invalidReason;
    private LocalDateTime visitedAt;
}
