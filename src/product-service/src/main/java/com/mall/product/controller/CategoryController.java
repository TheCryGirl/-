package com.mall.product.controller;

import com.mall.product.common.BizException;
import com.mall.product.common.ErrorCode;
import com.mall.product.common.Result;
import com.mall.product.dto.request.CreateCategoryRequest;
import com.mall.product.dto.request.SortCategoryRequest;
import com.mall.product.dto.request.UpdateCategoryRequest;
import com.mall.product.dto.response.CategoryResponse;
import com.mall.product.service.ProductService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
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
        return Result.success(productService.categories().stream()
                .map(category -> new CategoryResponse(category.getCategoryId(), category.getParentId(),
                        category.getName(), category.getSortNo()))
                .toList());
    }

    @PostMapping
    public Result<CategoryResponse> create(
            @RequestHeader(value = "X-User-Id", required = false) String adminId,
            @RequestHeader(value = "X-User-Role", required = false) String role,
            @Valid @RequestBody CreateCategoryRequest request) {
        requireAdmin(role);
        return Result.success(productService.createCategory(adminId, request));
    }

    @PutMapping("/{categoryId}")
    public Result<CategoryResponse> update(
            @PathVariable String categoryId,
            @RequestHeader(value = "X-User-Id", required = false) String adminId,
            @RequestHeader(value = "X-User-Role", required = false) String role,
            @Valid @RequestBody UpdateCategoryRequest request) {
        requireAdmin(role);
        return Result.success(productService.updateCategory(categoryId, adminId, request));
    }

    @PutMapping("/{categoryId}/sort")
    public Result<CategoryResponse> sort(
            @PathVariable String categoryId,
            @RequestHeader(value = "X-User-Id", required = false) String adminId,
            @RequestHeader(value = "X-User-Role", required = false) String role,
            @Valid @RequestBody SortCategoryRequest request) {
        requireAdmin(role);
        return Result.success(productService.sortCategory(categoryId, adminId, request.getSortNo()));
    }

    @DeleteMapping("/{categoryId}")
    public Result<Void> delete(
            @PathVariable String categoryId,
            @RequestHeader(value = "X-User-Id", required = false) String adminId,
            @RequestHeader(value = "X-User-Role", required = false) String role) {
        requireAdmin(role);
        productService.deleteCategory(categoryId, adminId);
        return Result.success();
    }

    private static void requireAdmin(String role) {
        if (!"ADMIN".equalsIgnoreCase(role)) {
            throw new BizException(ErrorCode.FORBIDDEN, "仅管理员可管理分类");
        }
    }
}
