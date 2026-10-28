package com.mall.product.dto.response;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * 店铺营业状态同步结果，affected 为实际变更状态的商品数。
 */
@Data
@AllArgsConstructor
public class VisibilitySyncResponse {

    private String sellerId;
    private Boolean visible;
    private Integer affected;
}
