package com.mall.product.dto.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class UpdateStockRequest {

    @NotNull(message = "可售库存不能为空")
    @Min(value = 0, message = "可售库存不能小于 0")
    private Integer stockAvailable;
}
