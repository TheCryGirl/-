package com.mall.user.entity;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 商品浏览足迹实体，与 browse_history 表一一对应。
 *
 * <p>同一用户同一商品只保留一条，浏览时间取最近一次。
 */
@Data
public class BrowseHistory {

    private String historyId;
    private String userId;
    private String productId;
    private LocalDateTime visitedAt;
}
