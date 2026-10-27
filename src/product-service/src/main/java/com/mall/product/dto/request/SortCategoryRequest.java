package com.mall.product.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class SortCategoryRequest {

    @NotNull(message = "排序号不能为空")
    @Min(value = 0, message = "排序号不能为负数")
    @Max(value = 999999, message = "排序号过大")
    private Integer sortNo;
}
