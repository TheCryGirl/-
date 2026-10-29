package com.mall.user.client.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * 通知 product-service 同步某卖家商品的可见性。
 *
 * <p>{@code visible=false} 时该卖家在售商品转为 SUSPENDED，{@code true} 时恢复为 ON_SALE。
 */
@Data
@AllArgsConstructor
public class SellerVisibilityRequest {

    private String sellerId;
    private Boolean visible;
}
