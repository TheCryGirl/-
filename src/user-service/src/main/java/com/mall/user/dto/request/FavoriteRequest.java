package com.mall.user.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 收藏 / 取消收藏入参。
 */
@Data
public class FavoriteRequest {

    @NotBlank(message = "商品ID不能为空")
    private String productId;
}
