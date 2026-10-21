package com.mall.product.service;

import com.mall.product.common.BizException;
import com.mall.product.common.ErrorCode;
import com.mall.product.common.IdGenerator;
import com.mall.product.common.PageResult;
import com.mall.product.common.ProductStatus;
import com.mall.product.dto.request.AuditProductRequest;
import com.mall.product.dto.request.CreateProductRequest;
import com.mall.product.dto.request.CreateSkuRequest;
import com.mall.product.dto.request.UpdateProductRequest;
import com.mall.product.dto.request.UpdateStatusRequest;
import com.mall.product.dto.request.UpdateStockRequest;
import com.mall.product.dto.response.ImageUploadResponse;
import com.mall.product.dto.response.ProductDetailResponse;
import com.mall.product.dto.response.ProductListItem;
import com.mall.product.dto.response.SkuResponse;
import com.mall.product.entity.Category;
import com.mall.product.entity.Product;
import com.mall.product.entity.Sku;
import com.mall.product.mapper.CategoryMapper;
import com.mall.product.mapper.ProductMapper;
import com.mall.product.mapper.SkuMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ProductService {

    private static final Set<String> SORT_WHITELIST = Set.of("default", "newest", "price_asc", "price_desc", "sales_desc");
    private static final Set<String> IMAGE_EXTENSIONS = Set.of("jpg", "jpeg", "png", "webp", "gif");

    private final ProductMapper productMapper;
    private final SkuMapper skuMapper;
    private final CategoryMapper categoryMapper;

    @Value("${product.upload-dir:uploads}")
    private String uploadDir;

    @Transactional
    public ProductDetailResponse create(String sellerId, CreateProductRequest request) {
        requireUser(sellerId);
        validateProductFields(request.getTitle(), request.getSubtitle(), request.getMainImage(), request.getCategoryId());
        validateSkus(request.getSkus());

        LocalDateTime now = LocalDateTime.now();
        Product product = Product.builder()
                .productId(IdGenerator.productId())
                .sellerId(sellerId)
                .title(request.getTitle().trim())
                .subtitle(trimToNull(request.getSubtitle()))
                .mainImage(trimToNull(request.getMainImage()))
                .categoryId(request.getCategoryId())
                .status(ProductStatus.PENDING)
                .salesCount(0L)
                .createdAt(now)
                .updatedAt(now)
                .createdBy(sellerId)
                .updatedBy(sellerId)
                .isDeleted(0)
                .build();
        productMapper.insert(product);

        for (CreateSkuRequest skuRequest : request.getSkus()) {
            Sku sku = Sku.builder()
                    .skuId(IdGenerator.skuId())
                    .productId(product.getProductId())
                    .specName(skuRequest.getSpecName().trim())
                    .price(skuRequest.getPrice())
                    .stockAvailable(skuRequest.getStockAvailable())
                    .stockLocked(0)
                    .version(0)
                    .createdAt(now)
                    .updatedAt(now)
                    .isDeleted(0)
                    .build();
            skuMapper.insert(sku);
        }
        return detailForView(product.getProductId(), sellerId, "SELLER");
    }

    @Transactional
    public ProductDetailResponse update(String productId, String sellerId, UpdateProductRequest request) {
        Product product = requireOwnedProduct(productId, sellerId);
        if (request.getTitle() != null) {
            if (request.getTitle().isBlank() || request.getTitle().trim().length() > 128) {
                throw new BizException(ErrorCode.PARAM_INVALID, "商品标题长度不合法");
            }
            product.setTitle(request.getTitle().trim());
        }
        if (request.getSubtitle() != null) {
            product.setSubtitle(trimToNull(request.getSubtitle()));
        }
        if (request.getMainImage() != null) {
            product.setMainImage(trimToNull(request.getMainImage()));
        }
        if (request.getCategoryId() != null) {
            validateCategory(request.getCategoryId());
            product.setCategoryId(request.getCategoryId());
        }
        product.setUpdatedAt(LocalDateTime.now());
        product.setUpdatedBy(sellerId);
        productMapper.updateInfo(product);
        return detailForView(productId, sellerId, "SELLER");
    }

    @Transactional
    public ProductDetailResponse updateStatus(String productId, String sellerId, UpdateStatusRequest request) {
        Product product = requireOwnedProduct(productId, sellerId);
        ProductStatus target;
        try {
            target = ProductStatus.valueOf(request.getStatus().trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new BizException(ErrorCode.PARAM_INVALID, "商品状态不合法");
        }
        if (target != ProductStatus.ON_SALE && target != ProductStatus.OFF_SHELF) {
            throw new BizException(ErrorCode.PARAM_INVALID, "卖家只能上架或下架商品");
        }
        if (target == ProductStatus.ON_SALE
                && (product.getStatus() == ProductStatus.PENDING || product.getStatus() == ProductStatus.REJECTED)) {
            throw new BizException(ErrorCode.FORBIDDEN, "商品尚未审核通过，不能上架");
        }
        productMapper.updateStatus(productId, target, sellerId);
        return detailForView(productId, sellerId, "SELLER");
    }

    @Transactional
    public ProductDetailResponse audit(String productId, String adminId, AuditProductRequest request) {
        Product product = productMapper.selectById(productId);
        if (product == null) {
            throw new BizException(ErrorCode.PRODUCT_NOT_FOUND, "商品不存在");
        }
        ProductStatus target = Boolean.TRUE.equals(request.getApproved())
                ? ProductStatus.ON_SALE : ProductStatus.REJECTED;
        productMapper.updateStatus(productId, target, adminId);
        return detailForView(productId, adminId, "ADMIN");
    }

    @Transactional
    public void delete(String productId, String sellerId) {
        requireOwnedProduct(productId, sellerId);
        productMapper.softDelete(productId, sellerId);
        skuMapper.softDeleteByProductId(productId, sellerId);
    }

    public PageResult<ProductListItem> list(String keyword, String categoryId, Long minPrice, Long maxPrice,
                                            String sort, int page, int size) {
        if (minPrice != null && minPrice < 0 || maxPrice != null && maxPrice < 0) {
            throw new BizException(ErrorCode.PARAM_INVALID, "价格区间不能为负数");
        }
        if (minPrice != null && maxPrice != null && minPrice > maxPrice) {
            throw new BizException(ErrorCode.PARAM_INVALID, "最低价不能高于最高价");
        }
        String safeSort = sort == null || sort.isBlank() ? "default" : sort.trim();
        if (!SORT_WHITELIST.contains(safeSort)) {
            throw new BizException(ErrorCode.PARAM_INVALID, "排序方式不合法");
        }
        int safePage = Math.max(1, page);
        int safeSize = Math.min(100, Math.max(1, size));
        List<ProductListItem> items = productMapper.selectPublicList(trimToNull(keyword), trimToNull(categoryId),
                minPrice, maxPrice, safeSort, (safePage - 1) * safeSize, safeSize);
        long total = productMapper.countPublic(trimToNull(keyword), trimToNull(categoryId), minPrice, maxPrice);
        return PageResult.of(items, total, safePage, safeSize);
    }

    public PageResult<ProductListItem> mine(String sellerId, String status, int page, int size) {
        requireUser(sellerId);
        if (status != null && !status.isBlank()) {
            try {
                ProductStatus.valueOf(status.trim().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException e) {
                throw new BizException(ErrorCode.PARAM_INVALID, "商品状态不合法");
            }
        }
        int safePage = Math.max(1, page);
        int safeSize = Math.min(100, Math.max(1, size));
        List<ProductListItem> items = productMapper.selectMine(sellerId, trimToNull(status),
                (safePage - 1) * safeSize, safeSize);
        long total = productMapper.countMine(sellerId, trimToNull(status));
        return PageResult.of(items, total, safePage, safeSize);
    }

    public ProductDetailResponse detail(String productId, String viewerId, String role) {
        return detailForView(productId, viewerId, role);
    }

    @Transactional
    public SkuResponse addSku(String productId, String sellerId, CreateSkuRequest request) {
        requireOwnedProduct(productId, sellerId);
        if (request == null || request.getSpecName() == null || request.getSpecName().isBlank()) {
            throw new BizException(ErrorCode.PARAM_INVALID, "规格名称不能为空");
        }
        if (request.getPrice() == null || request.getPrice() <= 0) {
            throw new BizException(ErrorCode.PARAM_INVALID, "价格必须大于 0 分");
        }
        if (request.getStockAvailable() == null || request.getStockAvailable() < 0) {
            throw new BizException(ErrorCode.PARAM_INVALID, "库存不能小于 0");
        }
        String specName = request.getSpecName().trim();
        boolean exists = skuMapper.selectByProductId(productId).stream()
                .anyMatch(sku -> sku.getSpecName().equalsIgnoreCase(specName));
        if (exists) {
            throw new BizException(ErrorCode.SPEC_CONFLICT, "规格组合已存在");
        }
        LocalDateTime now = LocalDateTime.now();
        Sku sku = Sku.builder()
                .skuId(IdGenerator.skuId())
                .productId(productId)
                .specName(specName)
                .price(request.getPrice())
                .stockAvailable(request.getStockAvailable())
                .stockLocked(0)
                .version(0)
                .createdAt(now)
                .updatedAt(now)
                .isDeleted(0)
                .build();
        try {
            skuMapper.insert(sku);
        } catch (DataIntegrityViolationException e) {
            throw new BizException(ErrorCode.SPEC_CONFLICT, "规格组合已存在", e);
        }
        return toSkuResponse(sku);
    }

    @Transactional
    public SkuResponse updateStock(String skuId, String sellerId, UpdateStockRequest request) {
        Sku sku = skuMapper.selectById(skuId);
        if (sku == null) {
            throw new BizException(ErrorCode.PRODUCT_NOT_FOUND, "SKU 不存在");
        }
        requireOwnedProduct(sku.getProductId(), sellerId);
        if (request.getStockAvailable() < 0) {
            throw new BizException(ErrorCode.PARAM_INVALID, "可售库存不能小于 0");
        }
        int updated = skuMapper.updateStockAvailable(skuId, request.getStockAvailable(), sellerId);
        if (updated == 0) {
            throw new BizException(ErrorCode.PRODUCT_NOT_FOUND, "SKU 不存在");
        }
        return toSkuResponse(skuMapper.selectById(skuId));
    }

    public List<Category> categories() {
        return categoryMapper.selectAll();
    }

    public ImageUploadResponse uploadImage(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BizException(ErrorCode.PARAM_INVALID, "图片不能为空");
        }
        if (file.getSize() > 5 * 1024 * 1024) {
            throw new BizException(ErrorCode.PARAM_INVALID, "图片不能超过 5MB");
        }
        String originalName = file.getOriginalFilename();
        String extension = originalName == null || !originalName.contains(".")
                ? "" : originalName.substring(originalName.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT);
        if (!IMAGE_EXTENSIONS.contains(extension)) {
            throw new BizException(ErrorCode.PARAM_INVALID, "只支持 jpg/jpeg/png/webp/gif 图片");
        }
        String filename = UUID.randomUUID().toString().replace("-", "") + "." + extension;
        try {
            Path directory = Paths.get(uploadDir).toAbsolutePath().normalize();
            Files.createDirectories(directory);
            Path target = directory.resolve(filename).normalize();
            if (!target.startsWith(directory)) {
                throw new BizException(ErrorCode.PARAM_INVALID, "文件名不合法");
            }
            try (InputStream inputStream = file.getInputStream()) {
                Files.copy(inputStream, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            throw new BizException(ErrorCode.SYSTEM_ERROR, "图片保存失败", e);
        }
        return new ImageUploadResponse("/images/" + filename);
    }

    private ProductDetailResponse detailForView(String productId, String viewerId, String role) {
        Product product = productMapper.selectById(productId);
        if (product == null) {
            throw new BizException(ErrorCode.PRODUCT_NOT_FOUND, "商品不存在");
        }
        boolean admin = "ADMIN".equalsIgnoreCase(role);
        boolean owner = viewerId != null && viewerId.equals(product.getSellerId());
        if (product.getStatus() != ProductStatus.ON_SALE && !admin && !owner) {
            throw new BizException(ErrorCode.PRODUCT_OFF_SHELF, "商品已下架或尚未审核通过");
        }
        List<Sku> skus = skuMapper.selectByProductId(productId);
        ProductDetailResponse response = new ProductDetailResponse();
        response.setProductId(product.getProductId());
        response.setSellerId(product.getSellerId());
        response.setTitle(product.getTitle());
        response.setSubtitle(product.getSubtitle());
        response.setMainImage(product.getMainImage());
        response.setCategoryId(product.getCategoryId());
        response.setStatus(product.getStatus());
        response.setPrice(skus.stream().mapToLong(Sku::getPrice).min().orElse(0L));
        response.setSkus(skus.stream().map(this::toSkuResponse).toList());
        return response;
    }

    private SkuResponse toSkuResponse(Sku sku) {
        return new SkuResponse(sku.getSkuId(), sku.getSpecName(), sku.getPrice(),
                sku.getStockAvailable(), sku.getStockLocked());
    }

    private Product requireOwnedProduct(String productId, String sellerId) {
        requireUser(sellerId);
        Product product = productMapper.selectById(productId);
        if (product == null) {
            throw new BizException(ErrorCode.PRODUCT_NOT_FOUND, "商品不存在");
        }
        if (!sellerId.equals(product.getSellerId())) {
            throw new BizException(ErrorCode.FORBIDDEN, "无权操作他人的商品");
        }
        return product;
    }

    private void validateProductFields(String title, String subtitle, String mainImage, String categoryId) {
        if (title == null || title.isBlank() || title.trim().length() > 128) {
            throw new BizException(ErrorCode.PARAM_INVALID, "商品标题不能为空且不能超过 128 字");
        }
        if (subtitle != null && subtitle.length() > 255) {
            throw new BizException(ErrorCode.PARAM_INVALID, "商品副标题不能超过 255 字");
        }
        if (mainImage != null && mainImage.length() > 255) {
            throw new BizException(ErrorCode.PARAM_INVALID, "图片地址不能超过 255 字");
        }
        validateCategory(categoryId);
    }

    private void validateCategory(String categoryId) {
        if (categoryId == null || categoryId.isBlank()) {
            throw new BizException(ErrorCode.PARAM_INVALID, "商品分类不能为空");
        }
        boolean exists = categoryMapper.selectAll().stream()
                .anyMatch(category -> category.getCategoryId().equals(categoryId));
        if (!exists) {
            throw new BizException(ErrorCode.PARAM_INVALID, "商品分类不存在");
        }
    }

    private void validateSkus(List<CreateSkuRequest> skus) {
        if (skus == null || skus.isEmpty()) {
            throw new BizException(ErrorCode.PARAM_INVALID, "至少填写一个 SKU");
        }
        Set<String> specs = new HashSet<>();
        for (CreateSkuRequest sku : skus) {
            if (sku == null || sku.getSpecName() == null || sku.getSpecName().isBlank()) {
                throw new BizException(ErrorCode.PARAM_INVALID, "规格名称不能为空");
            }
            if (sku.getPrice() == null || sku.getPrice() <= 0) {
                throw new BizException(ErrorCode.PARAM_INVALID, "价格必须大于 0 分");
            }
            if (sku.getStockAvailable() == null || sku.getStockAvailable() < 0) {
                throw new BizException(ErrorCode.PARAM_INVALID, "库存不能小于 0");
            }
            String spec = sku.getSpecName().trim().toLowerCase(Locale.ROOT);
            if (!specs.add(spec)) {
                throw new BizException(ErrorCode.SPEC_CONFLICT, "规格组合重复");
            }
        }
    }

    private static void requireUser(String userId) {
        if (userId == null || userId.isBlank()) {
            throw new BizException(ErrorCode.UNAUTHORIZED, "未登录或用户信息缺失");
        }
    }

    private static String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
