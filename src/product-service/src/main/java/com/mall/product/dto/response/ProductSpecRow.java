package com.mall.product.dto.response;

import lombok.Data;

/**
 * SKU 规格名（#141）：检索时规格名参与匹配，评分权重按分类命中处理。
 */
@Data
public class ProductSpecRow {

    private String productId;
    private String specName;
}
