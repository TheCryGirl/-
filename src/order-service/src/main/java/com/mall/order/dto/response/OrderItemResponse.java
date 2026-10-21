package com.mall.order.dto.response;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class OrderItemResponse {
    private String skuId;
    private String productTitle;
    private String specName;
    private Long priceSnapshot;
    private Integer quantity;
    private Long subtotal;
    private String mainImage;
}
