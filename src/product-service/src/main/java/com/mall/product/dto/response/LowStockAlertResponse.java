package com.mall.product.dto.response;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class LowStockAlertResponse {
    private String skuId;
    private String productId;
    private String productTitle;
    private String specName;
    private Integer stockAvailable;
    private Integer stockLocked;
    private Integer threshold;
}
