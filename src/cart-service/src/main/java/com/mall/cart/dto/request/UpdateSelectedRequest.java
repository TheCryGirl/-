package com.mall.cart.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class UpdateSelectedRequest {

    @NotNull(message = "勾选状态不能为空")
    private Boolean selected;
}
