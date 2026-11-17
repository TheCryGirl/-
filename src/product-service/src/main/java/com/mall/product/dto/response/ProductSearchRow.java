package com.mall.product.dto.response;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 检索 / 推荐用的商品行（#141 内部 DTO）。
 *
 * <p>比列表项多带 {@code categoryName}、{@code salesCount}：前者参与相关度评分，
 * 后者作为「热销」排序依据。两个字段不直接对外暴露。
 */
@Data
public class ProductSearchRow {

    private String productId;
    private String sellerId;
    private String title;
    private String subtitle;
    private String mainImage;
    private String categoryId;
    private String categoryName;
    private String status;
    private Long salesCount;
    private LocalDateTime createdAt;
    /** 该商品最低 SKU 价，单位分 */
    private Long price;
    /** 该商品可售库存合计 */
    private Integer stock;
}
