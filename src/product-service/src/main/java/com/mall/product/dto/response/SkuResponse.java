package com.mall.product.dto.response;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class SkuResponse {
    private String skuId;
    private String specName;
    private Long price;
    private Integer stockAvailable;
    private Integer stockLocked;
}
