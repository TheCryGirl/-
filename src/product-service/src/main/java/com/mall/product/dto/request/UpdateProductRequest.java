package com.mall.product.dto.request;

import lombok.Data;

@Data
public class UpdateProductRequest {
    private String title;
    private String subtitle;
    private String mainImage;
    private String categoryId;
}
