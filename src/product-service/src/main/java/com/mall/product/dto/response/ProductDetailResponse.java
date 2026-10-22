package com.mall.product.dto.response;

import com.mall.product.common.ProductStatus;
import lombok.Data;

import java.util.List;

@Data
public class ProductDetailResponse {
    private String productId;
    private String sellerId;
    private String title;
    private String subtitle;
    private String mainImage;
    private String categoryId;
    private ProductStatus status;
    private Long price;
    private List<SkuResponse> skus;
}
