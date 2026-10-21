package com.mall.product.dto.response;

import com.mall.product.common.ProductStatus;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class ProductListItem {
    private String productId;
    private String sellerId;
    private String title;
    private String subtitle;
    private String mainImage;
    private String categoryId;
    private ProductStatus status;
    private Long price;
    private Integer stock;
    private LocalDateTime createdAt;
}
