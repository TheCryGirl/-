package com.mall.product.dto.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class UpdateLowStockThresholdRequest {

    @NotNull(message = "低库存阈值不能为空")
    @Min(value = 0, message = "低库存阈值不能为负数")
    private Integer threshold;
}
