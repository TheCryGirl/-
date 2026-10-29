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
    /** 分类名，取自 mall_product.category；分类被删除时为 null，前端显示“未分类” */
    private String categoryName;
    private ProductStatus status;
    private Long price;
    private List<SkuResponse> skus;
}
