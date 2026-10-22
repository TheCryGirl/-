package com.mall.product.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

@Data
public class CreateProductRequest {

    @NotBlank(message = "商品标题不能为空")
    private String title;

    private String subtitle;
    private String mainImage;

    @NotBlank(message = "商品分类不能为空")
    private String categoryId;

    @Valid
    @NotEmpty(message = "至少填写一个 SKU")
    private List<CreateSkuRequest> skus;
}
