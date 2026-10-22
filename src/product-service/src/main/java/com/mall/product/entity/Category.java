package com.mall.product.entity;

import lombok.Data;

@Data
public class Category {
    private String categoryId;
    private String parentId;
    private String name;
    private Integer sortNo;
    private Integer isDeleted;
}
