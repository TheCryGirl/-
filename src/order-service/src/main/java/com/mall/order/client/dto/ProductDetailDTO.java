package com.mall.order.client.dto;

import lombok.Data;

import java.util.List;

@Data
public class ProductDetailDTO {
    private String productId;
    private String sellerId;
    private String title;
    private String subtitle;
    private String mainImage;
    private String categoryId;
    private String status;
    private Long price;
    private List<SkuDTO> skus;
}
