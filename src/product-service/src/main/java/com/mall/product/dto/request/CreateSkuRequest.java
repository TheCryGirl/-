package com.mall.product.dto.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class CreateSkuRequest {

    @NotBlank(message = "规格名称不能为空")
    private String specName;

    @NotNull(message = "价格不能为空")
    @Min(value = 1, message = "价格必须大于 0 分")
    private Long price;

    @NotNull(message = "库存不能为空")
    @Min(value = 0, message = "库存不能小于 0")
    private Integer stockAvailable;
}
