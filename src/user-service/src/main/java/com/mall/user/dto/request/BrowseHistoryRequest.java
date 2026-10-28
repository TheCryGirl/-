package com.mall.user.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 记录浏览足迹入参。
 */
@Data
public class BrowseHistoryRequest {

    @NotBlank(message = "商品ID不能为空")
    private String productId;
}
