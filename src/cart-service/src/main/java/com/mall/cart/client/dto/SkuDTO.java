package com.mall.cart.client.dto;

import lombok.Data;

@Data
public class SkuDTO {

    private String skuId;
    private String specName;
    private Long price;
    private Integer stockAvailable;
    private Integer stockLocked;
}
