package com.mall.user.dto.response;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 收藏夹条目。
 *
 * <p>商品标题、图片、价格与状态来自 product-service（不跨库 JOIN）；
 * 商品已下架或店铺停用时 {@code invalid} 为 true，前端置灰并展示 {@code invalidReason}。
 */
@Data
public class FavoriteResponse {

    private String favoriteId;
    private String productId;
    private String title;
    private String mainImage;
    private Long price;
    private String status;
    private Boolean invalid;
    private String invalidReason;
    private LocalDateTime createdAt;
}
