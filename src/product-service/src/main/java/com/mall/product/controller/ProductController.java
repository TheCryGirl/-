package com.mall.product.controller;

import com.mall.product.common.BizException;
import com.mall.product.common.ErrorCode;
import com.mall.product.common.PageResult;
import com.mall.product.common.Result;
import com.mall.product.config.InternalTokenVerifier;
import com.mall.product.dto.request.AuditProductRequest;
import com.mall.product.dto.request.CreateProductRequest;
import com.mall.product.dto.request.CreateSkuRequest;
import com.mall.product.dto.request.SellerVisibilityRequest;
import com.mall.product.dto.request.UpdateProductRequest;
import com.mall.product.dto.request.UpdateStatusRequest;
import com.mall.product.dto.request.UpdateStockRequest;
import com.mall.product.dto.response.ImageUploadResponse;
import com.mall.product.dto.response.ProductBriefResponse;
import com.mall.product.dto.response.ProductDetailResponse;
import com.mall.product.dto.response.ProductListItem;
import com.mall.product.dto.response.SkuResponse;
import com.mall.product.dto.response.VisibilitySyncResponse;
import com.mall.product.service.AuditLogPublisher;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping({"/products", "/api/products"})
@RequiredArgsConstructor
public class ProductController {

    private final ProductService productService;
    private final InternalTokenVerifier internalTokenVerifier;
    private final AuditLogPublisher auditLogPublisher;

    /**
     * 商品列表。带 status 参数时仅对管理员生效（商品审核页按状态筛选），
     * 普通用户仍按 BR-08 只看在售且在库商品。
     */
    @GetMapping
    public Result<PageResult<ProductListItem>> list(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String categoryId,
            @RequestParam(required = false) Long minPrice,
            @RequestParam(required = false) Long maxPrice,
            @RequestParam(defaultValue = "default") String sort,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestHeader(value = "X-User-Role", required = false) String role) {
        return Result.success(productService.list(keyword, categoryId, minPrice, maxPrice, sort, status, role, page, size));
    }

    /**
     * 批量取商品简要信息（#52）。收藏夹与足迹展示用，不按状态过滤，
     * 失效商品也要能返回给前端置灰。
     */
    @GetMapping("/batch")
    public Result<List<ProductBriefResponse>> batch(@RequestParam(required = false) String ids) {
        return Result.success(productService.batch(ids));
    }

    /**
     * 店铺停用 / 恢复后同步该卖家商品的可见性（#49），仅内部服务调用。
     */
    @PostMapping("/internal/seller-visibility")
    public Result<VisibilitySyncResponse> syncSellerVisibility(
            @RequestHeader(value = "X-Internal-Token", required = false) String token,
            @Valid @RequestBody SellerVisibilityRequest request) {
        internalTokenVerifier.verify(token);
        return Result.success(productService.updateSellerVisibility(request.getSellerId(), request.getVisible()));
    }

    @GetMapping("/mine")
    public Result<PageResult<ProductListItem>> mine(
            @RequestHeader(value = "X-User-Id", required = false) String sellerId,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        return Result.success(productService.mine(sellerId, status, page, size));
    }

    @GetMapping("/{productId}")
    public Result<ProductDetailResponse> detail(
            @PathVariable String productId,
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @RequestHeader(value = "X-User-Role", required = false) String role) {
        return Result.success(productService.detail(productId, userId, role));
    }

    @PostMapping
    public Result<ProductDetailResponse> create(
            @RequestHeader(value = "X-User-Id", required = false) String sellerId,
            @Valid @RequestBody CreateProductRequest request) {
        return Result.success(productService.create(sellerId, request));
    }

    @PutMapping("/{productId}")
    public Result<ProductDetailResponse> update(
            @PathVariable String productId,
            @RequestHeader(value = "X-User-Id", required = false) String sellerId,
            @Valid @RequestBody UpdateProductRequest request) {
        return Result.success(productService.update(productId, sellerId, request));
    }

    @PutMapping("/{productId}/status")
    public Result<ProductDetailResponse> updateStatus(
            @PathVariable String productId,
            @RequestHeader(value = "X-User-Id", required = false) String sellerId,
            @Valid @RequestBody UpdateStatusRequest request) {
        return Result.success(productService.updateStatus(productId, sellerId, request));
    }

    @PutMapping("/{productId}/audit")
    public Result<ProductDetailResponse> audit(
            @PathVariable String productId,
            @RequestHeader(value = "X-User-Id", required = false) String adminId,
            @RequestHeader(value = "X-User-Role", required = false) String role,
            @Valid @RequestBody AuditProductRequest request) {
        if (!"ADMIN".equalsIgnoreCase(role)) {
            throw new BizException(ErrorCode.FORBIDDEN, "无权限审核商品");
        }
        ProductDetailResponse audited = productService.audit(productId, adminId, request);
        auditLogPublisher.publish(adminId, role, "PRODUCT_AUDIT", "PRODUCT", productId,
                (Boolean.TRUE.equals(request.getApproved()) ? "审核通过" : "审核驳回") + suffix(request.getRemark()));
        return Result.success(audited);
    }

    @DeleteMapping("/{productId}")
    public Result<Void> delete(
            @PathVariable String productId,
            @RequestHeader(value = "X-User-Id", required = false) String sellerId) {
        productService.delete(productId, sellerId);
        return Result.success();
    }

    @PostMapping("/{productId}/skus")
    public Result<SkuResponse> addSku(
            @PathVariable String productId,
            @RequestHeader(value = "X-User-Id", required = false) String sellerId,
            @Valid @RequestBody CreateSkuRequest request) {
        return Result.success(productService.addSku(productId, sellerId, request));
    }

    @PutMapping("/skus/{skuId}/stock")
    public Result<SkuResponse> updateStock(
            @PathVariable String skuId,
            @RequestHeader(value = "X-User-Id", required = false) String sellerId,
            @Valid @RequestBody UpdateStockRequest request) {
        return Result.success(productService.updateStock(skuId, sellerId, request));
    }

    @PostMapping("/images")
    public Result<ImageUploadResponse> uploadImage(@RequestParam("file") MultipartFile file) {
        return Result.success(productService.uploadImage(file));
    }

    private static String suffix(String remark) {
        return remark == null || remark.isBlank() ? "" : "：" + remark;
    }
}
