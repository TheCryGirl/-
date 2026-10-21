package com.mall.cart.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class UpdateQuantityRequest {

    @NotNull(message = "数量不能为空")
    @Min(value = 1, message = "数量不能小于 1")
    @Max(value = 999, message = "数量不能超过 999")
    private Integer quantity;
}
