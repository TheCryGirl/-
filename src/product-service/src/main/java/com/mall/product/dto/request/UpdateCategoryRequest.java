package com.mall.product.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class UpdateCategoryRequest {

    @Size(max = 32, message = "分类名称不能超过 32 个字符")
    private String name;

    private String parentId;

    @Min(value = 0, message = "排序号不能为负数")
    @Max(value = 999999, message = "排序号过大")
    private Integer sortNo;
}
