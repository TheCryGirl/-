package com.mall.product.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class ReserveItem {

    @NotBlank(message = "skuId 不能为空")
    private String skuId;

    @NotNull(message = "预占数量不能为空")
    @Min(value = 1, message = "预占数量必须大于 0")
    @Max(value = 999, message = "单个 SKU 最多预占 999 件")
    private Integer quantity;
}
