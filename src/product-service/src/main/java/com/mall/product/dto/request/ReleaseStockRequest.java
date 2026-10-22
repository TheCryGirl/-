package com.mall.product.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class ReleaseStockRequest {

    @NotBlank(message = "orderId 不能为空")
    private String orderId;
}
