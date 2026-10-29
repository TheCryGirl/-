package com.mall.product.service;

import com.mall.product.client.ShopClient;
import com.mall.product.client.dto.ShopBriefDTO;
import com.mall.product.common.BizException;
import com.mall.product.common.ErrorCode;
import com.mall.product.common.IdGenerator;
import com.mall.product.common.PageResult;
import com.mall.product.common.ProductStatus;
import com.mall.product.common.Result;
import com.mall.product.dto.request.AuditProductRequest;
import com.mall.product.dto.request.CreateCategoryRequest;
import com.mall.product.dto.request.CreateProductRequest;
import com.mall.product.dto.request.CreateSkuRequest;
import com.mall.product.dto.request.UpdateCategoryRequest;
import com.mall.product.dto.request.UpdateProductRequest;
import com.mall.product.dto.request.UpdateStatusRequest;
import com.mall.product.dto.request.UpdateStockRequest;
import com.mall.product.dto.response.CategoryResponse;
import com.mall.product.dto.response.ImageUploadResponse;
import com.mall.product.dto.response.ProductBriefResponse;
import com.mall.product.dto.response.ProductDetailResponse;
import com.mall.product.dto.response.ProductListItem;
import com.mall.product.dto.response.SkuResponse;
import com.mall.product.dto.response.VisibilitySyncResponse;
import com.mall.product.entity.Category;
import com.mall.product.entity.Product;
import com.mall.product.entity.Sku;
import com.mall.product.mapper.CategoryMapper;
import com.mall.product.mapper.ProductMapper;
import com.mall.product.mapper.SkuMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProductService {

    private static final Set<String> SORT_WHITELIST = Set.of("default", "newest", "price_asc", "price_desc", "sales_desc");
    private static final Set<String> IMAGE_EXTENSIONS = Set.of("jpg", "jpeg", "png", "webp", "gif");
    private static final int MAX_BATCH_SIZE = 100;

    private final ProductMapper productMapper;
    private final SkuMapper skuMapper;
    private final CategoryMapper categoryMapper;
    private final ShopClient shopClient;

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
                    .lowStockThreshold(skuRequest.getLowStockThreshold())
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
        if (target == ProductStatus.ON_SALE) {
            if (product.getStatus() == ProductStatus.PENDING || product.getStatus() == ProductStatus.REJECTED) {
                throw new BizException(ErrorCode.FORBIDDEN, "商品尚未审核通过，不能上架");
            }
            // 店铺被停用时卖家不能自行把商品放回在售（#49）
            if (!shopActive(sellerId)) {
                throw new BizException(ErrorCode.SHOP_SUSPENDED, "店铺已被停用，暂不能上架商品");
            }
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
        ProductStatus target;
        if (Boolean.TRUE.equals(request.getApproved())) {
            // 店铺停用期间审核通过不能直接上架，先挂起，恢复营业时由同步接口改回在售
            target = shopActive(product.getSellerId()) ? ProductStatus.ON_SALE : ProductStatus.SUSPENDED;
        } else {
            target = ProductStatus.REJECTED;
        }
        productMapper.updateStatus(productId, target, adminId);
        return detailForView(productId, adminId, "ADMIN");
    }

    @Transactional
    public void delete(String productId, String sellerId) {
        requireOwnedProduct(productId, sellerId);
        productMapper.softDelete(productId, sellerId);
        skuMapper.softDeleteByProductId(productId, sellerId);
    }

    /**
     * 商品列表。普通用户走公开列表（BR-08：只返回在售且在库商品）；
     * 管理员带 status 参数时走管理列表，用于商品审核页按状态筛选。
     */
    public PageResult<ProductListItem> list(String keyword, String categoryId, Long minPrice, Long maxPrice,
                                            String sort, String status, String role, int page, int size) {
        if ("ADMIN".equalsIgnoreCase(role) && status != null && !status.isBlank()) {
            return listForAdmin(status, page, size);
        }
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

    /**
     * 管理员商品列表：可按状态筛选，不过滤库存与上架状态，供审核页使用。
     */
    private PageResult<ProductListItem> listForAdmin(String status, int page, int size) {
        String safeStatus = status.trim().toUpperCase(Locale.ROOT);
        try {
            ProductStatus.valueOf(safeStatus);
        } catch (IllegalArgumentException e) {
            throw new BizException(ErrorCode.PARAM_INVALID, "商品状态不合法");
        }
        int safePage = Math.max(1, page);
        int safeSize = Math.min(100, Math.max(1, size));
        List<ProductListItem> items = productMapper.selectAdminList(safeStatus, (safePage - 1) * safeSize, safeSize);
        long total = productMapper.countAdmin(safeStatus);
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
                .lowStockThreshold(request.getLowStockThreshold())
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

    /**
     * 分类列表（#50）。顶级分类按 sort_no 排序，子分类紧跟在自己的父分类后面，
     * 前端按返回顺序渲染即为层级顺序；结构保持扁平，兼容已有调用方。
     */
    public List<Category> categories() {
        List<Category> all = categoryMapper.selectAll();
        Map<String, List<Category>> children = new LinkedHashMap<>();
        for (Category category : all) {
            if (isRootCategory(category)) {
                continue;
            }
            children.computeIfAbsent(category.getParentId(), key -> new ArrayList<>()).add(category);
        }
        List<Category> ordered = new ArrayList<>(all.size());
        Set<String> added = new HashSet<>();
        for (Category category : all) {
            if (!isRootCategory(category)) {
                continue;
            }
            ordered.add(category);
            added.add(category.getCategoryId());
            for (Category child : children.getOrDefault(category.getCategoryId(), List.of())) {
                ordered.add(child);
                added.add(child.getCategoryId());
            }
        }
        // 父分类已删除的孤儿分类追加到末尾，避免前端下拉丢选项
        for (Category category : all) {
            if (!added.contains(category.getCategoryId())) {
                ordered.add(category);
            }
        }
        return ordered;
    }

    /**
     * 批量取商品简要信息（#52）。收藏夹与足迹的展示字段都从里取，user-service 不跨库读商品表。
     * 不按状态过滤：失效商品也要展示，由调用方根据 status 置灰并标注原因。
     */
    public List<ProductBriefResponse> batch(String ids) {
        List<String> productIds = parseProductIds(ids);
        if (productIds.isEmpty()) {
            return List.of();
        }
        List<ProductBriefResponse> products = productMapper.selectBriefByIds(productIds);
        // 没有 SKU 的商品给 0，前端按“暂无报价”处理
        products.forEach(product -> {
            if (product.getPrice() == null) {
                product.setPrice(0L);
            }
        });
        return products;
    }

    /**
     * 同步卖家商品可见性（#49），由 user-service 在管理员停用 / 恢复店铺后调用。
     */
    @Transactional
    public VisibilitySyncResponse updateSellerVisibility(String sellerId, boolean visible) {
        requireUser(sellerId);
        int affected = visible
                ? productMapper.resumeBySellerId(sellerId, sellerId)
                : productMapper.suspendBySellerId(sellerId, sellerId);
        log.info("同步卖家商品可见性, sellerId={}, visible={}, affected={}", sellerId, visible, affected);
        return new VisibilitySyncResponse(sellerId, visible, affected);
    }

    private List<String> parseProductIds(String ids) {
        if (ids == null || ids.isBlank()) {
            return List.of();
        }
        Set<String> unique = new LinkedHashSet<>();
        for (String part : ids.split(",")) {
            String id = part.trim();
            if (id.isEmpty()) {
                continue;
            }
            unique.add(id);
            if (unique.size() >= MAX_BATCH_SIZE) {
                break;
            }
        }
        return new ArrayList<>(unique);
    }

    private static boolean isRootCategory(Category category) {
        return category.getParentId() == null || category.getParentId().isBlank()
                || "0".equals(category.getParentId());
    }

    /**
     * 店铺是否在营业。
     *
     * <p>用户服务不可用或查不到店铺时按“正常营业”处理，避免商品服务单机调试时审核链路整体不可用；
     * 真正的停用由 {@link #updateSellerVisibility} 同步兜底。
     */
    private boolean shopActive(String sellerId) {
        try {
            Result<ShopBriefDTO> result = shopClient.getBySeller(sellerId);
            if (result == null || result.getCode() != ErrorCode.SUCCESS || result.getData() == null) {
                return true;
            }
            return !"SUSPENDED".equalsIgnoreCase(result.getData().getStatus());
        } catch (Exception e) {
            // 店铺服务不可用时按正常营业放行，避免单服务调试或集成测试整体不可用
            log.warn("查询店铺营业状态失败，按正常营业处理, sellerId={}", sellerId, e);
            return true;
        }
    }

    @Transactional
    public CategoryResponse createCategory(String adminId, CreateCategoryRequest request) {
        requireUser(adminId);
        validateCategoryRequest(request.getName(), request.getParentId(), null);
        Category category = new Category();
        category.setCategoryId("C" + IdGenerator.randomHex(10));
        category.setParentId(trimToNull(request.getParentId()));
        category.setName(request.getName().trim());
        category.setSortNo(request.getSortNo() == null ? 0 : request.getSortNo());
        category.setIsDeleted(0);
        try {
            categoryMapper.insert(category);
        } catch (DataIntegrityViolationException e) {
            throw new BizException(ErrorCode.PARAM_INVALID, "分类已存在或参数不合法", e);
        }
        return toCategoryResponse(category);
    }

    @Transactional
    public CategoryResponse updateCategory(String categoryId, String adminId, UpdateCategoryRequest request) {
        requireUser(adminId);
        Category category = requireCategory(categoryId);
        String name = request.getName() == null ? category.getName() : request.getName().trim();
        String parentId = request.getParentId() == null ? category.getParentId() : trimToNull(request.getParentId());
        Integer sortNo = request.getSortNo() == null ? category.getSortNo() : request.getSortNo();
        validateCategoryRequest(name, parentId, categoryId);
        category.setName(name);
        category.setParentId(parentId);
        category.setSortNo(sortNo == null ? 0 : sortNo);
        categoryMapper.update(category);
        return toCategoryResponse(category);
    }

    @Transactional
    public CategoryResponse sortCategory(String categoryId, String adminId, int sortNo) {
        requireUser(adminId);
        Category category = requireCategory(categoryId);
        if (sortNo < 0) {
            throw new BizException(ErrorCode.PARAM_INVALID, "排序号不能为负数");
        }
        category.setSortNo(sortNo);
        categoryMapper.update(category);
        return toCategoryResponse(category);
    }

    @Transactional
    public void deleteCategory(String categoryId, String adminId) {
        requireUser(adminId);
        requireCategory(categoryId);
        if (categoryMapper.countProducts(categoryId) > 0) {
            throw new BizException(ErrorCode.PARAM_INVALID, "分类下存在商品，请先处理商品");
        }
        if (categoryMapper.countChildren(categoryId) > 0) {
            throw new BizException(ErrorCode.PARAM_INVALID, "分类下存在子分类，请先处理子分类");
        }
        categoryMapper.softDelete(categoryId, adminId);
    }

    private Category requireCategory(String categoryId) {
        Category category = categoryMapper.selectById(categoryId);
        if (category == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "分类不存在");
        }
        return category;
    }

    private void validateCategoryRequest(String name, String parentId, String categoryId) {
        if (name == null || name.isBlank() || name.trim().length() > 32) {
            throw new BizException(ErrorCode.PARAM_INVALID, "分类名称不能为空且不能超过 32 个字符");
        }
        if (parentId != null) {
            if (parentId.equals(categoryId)) {
                throw new BizException(ErrorCode.PARAM_INVALID, "分类不能设置自己为父分类");
            }
            if (categoryMapper.selectById(parentId) == null) {
                throw new BizException(ErrorCode.PARAM_INVALID, "父分类不存在");
            }
        }
    }

    private static CategoryResponse toCategoryResponse(Category category) {
        return new CategoryResponse(category.getCategoryId(), category.getParentId(), category.getName(), category.getSortNo());
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
            if (product.getStatus() == ProductStatus.SUSPENDED) {
                throw new BizException(ErrorCode.SHOP_SUSPENDED, "店铺已停用，商品暂不可购买");
            }
            throw new BizException(ErrorCode.PRODUCT_OFF_SHELF, "商品已下架或尚未审核通过");
        }
        if (!admin && !owner && categoryMapper.selectById(product.getCategoryId()) == null) {
            throw new BizException(ErrorCode.PRODUCT_OFF_SHELF, "商品分类已删除");
        }        List<Sku> skus = skuMapper.selectByProductId(productId);
        ProductDetailResponse response = new ProductDetailResponse();
        response.setProductId(product.getProductId());
        response.setSellerId(product.getSellerId());
        response.setTitle(product.getTitle());
        response.setSubtitle(product.getSubtitle());
        response.setMainImage(product.getMainImage());
        response.setCategoryId(product.getCategoryId());
        response.setCategoryName(categoryName(product.getCategoryId()));
        response.setStatus(product.getStatus());
        response.setPrice(skus.stream().mapToLong(Sku::getPrice).min().orElse(0L));
        response.setSkus(skus.stream().map(this::toSkuResponse).toList());
        return response;
    }

    private String categoryName(String categoryId) {
        if (categoryId == null || categoryId.isBlank()) {
            return null;
        }
        Category category = categoryMapper.selectById(categoryId);
        return category == null ? null : category.getName();
    }

    private SkuResponse toSkuResponse(Sku sku) {
        return new SkuResponse(sku.getSkuId(), sku.getSpecName(), sku.getPrice(),
                sku.getStockAvailable(), sku.getStockLocked(), sku.getLowStockThreshold());
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


