package com.mall.product.controller;

import com.mall.product.common.Result;
import com.mall.product.dto.response.CategoryResponse;
import com.mall.product.service.ProductService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping({"/categories", "/api/categories"})
@RequiredArgsConstructor
public class CategoryController {

    private final ProductService productService;

    @GetMapping
    public Result<List<CategoryResponse>> list() {
        List<CategoryResponse> categories = productService.categories().stream()
                .map(category -> new CategoryResponse(category.getCategoryId(), category.getParentId(),
                        category.getName(), category.getSortNo()))
                .toList();
        return Result.success(categories);
    }
}
