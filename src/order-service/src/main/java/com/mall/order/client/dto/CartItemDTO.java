package com.mall.order.client.dto;

import lombok.Data;

@Data
public class CartItemDTO {
    private String cartItemId;
    private String skuId;
    private String productId;
    private String title;
    private String specName;
    private String mainImage;
    private Long price;
    private Integer quantity;
    private Boolean selected;
    private Integer stockAvailable;
    private Boolean invalid;
    private String invalidReason;
}
