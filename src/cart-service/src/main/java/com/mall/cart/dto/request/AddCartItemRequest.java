package com.mall.cart.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class AddCartItemRequest {

    @NotBlank(message = "SKU 不能为空")
    @Size(max = 32, message = "SKU ID 不合法")
    private String skuId;

    @NotNull(message = "数量不能为空")
    @Min(value = 1, message = "数量不能小于 1")
    @Max(value = 999, message = "数量不能超过 999")
    private Integer quantity;

    /**
     * 商品 ID：cart-service 只能按 productId 调商品详情（契约 4.4），
     * 契约 4.3 的请求示例遗漏了该字段，补充说明见交付文档。
     */
    @Size(max = 32, message = "商品 ID 不合法")
    private String productId;
}
