package com.mall.user.client.dto;

import lombok.Data;

/**
 * product-service 返回的商品简要信息，用于收藏夹与足迹展示。
 *
 * <p>按《05-接口契约》10.1 不跨库 JOIN，商品字段一律通过接口获取。
 */
@Data
public class ProductBriefDTO {

    private String productId;
    private String sellerId;
    private String title;
    private String mainImage;
    private Long price;
    private String status;
}
