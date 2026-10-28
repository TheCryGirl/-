package com.mall.product.mapper;

import com.mall.product.entity.Category;
import org.apache.ibatis.annotations.Param;

import java.util.List;

public interface CategoryMapper {
    List<Category> selectAll();

    Category selectById(@Param("categoryId") String categoryId);

    int insert(Category category);

    int update(Category category);

    int softDelete(@Param("categoryId") String categoryId, @Param("updatedBy") String updatedBy);

    long countProducts(@Param("categoryId") String categoryId);

    long countChildren(@Param("categoryId") String categoryId);
}