package com.mall.product;

import com.mall.product.client.ShopClient;
import com.mall.product.common.BizException;
import com.mall.product.common.Result;
import com.mall.product.common.ErrorCode;
import com.mall.product.common.ProductStatus;
import com.mall.product.dto.request.AuditProductRequest;
import com.mall.product.dto.request.CreateCategoryRequest;
import com.mall.product.dto.request.CreateProductRequest;
import com.mall.product.dto.request.CreateSkuRequest;
import com.mall.product.dto.request.UpdateCategoryRequest;
import com.mall.product.dto.request.UpdateStockRequest;
import com.mall.product.dto.response.ProductDetailResponse;
import com.mall.product.mapper.SkuMapper;
import com.mall.product.service.ProductService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@SpringBootTest
@ActiveProfiles("test")
class ProductServiceTest {

    @Autowired
    private ProductService productService;

    @Autowired
    private SkuMapper skuMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @MockBean
    private ShopClient shopClient;

    @BeforeEach
    void clean() {
        when(shopClient.publication(anyString()))
                .thenReturn(Result.success(new ShopClient.PublicationEligibility(true, null)));
        jdbcTemplate.update("DELETE FROM stock_reservation_item");
        jdbcTemplate.update("DELETE FROM stock_reservation");
        jdbcTemplate.update("DELETE FROM sku");
        jdbcTemplate.update("DELETE FROM product");
    }

    @Test
    void createRejectsUnapprovedShopWithoutWritingProduct() {
        when(shopClient.publication("U-PENDING"))
                .thenReturn(Result.success(new ShopClient.PublicationEligibility(false, "请先完成店铺入驻审核")));
        CreateSkuRequest sku = new CreateSkuRequest();
        sku.setSpecName("默认规格");
        sku.setPrice(1000L);
        sku.setStockAvailable(1);
        CreateProductRequest request = new CreateProductRequest();
        request.setTitle("待审核店铺商品");
        request.setCategoryId("C04");
        request.setSkus(List.of(sku));

        assertThatThrownBy(() -> productService.create("U-PENDING", request))
                .isInstanceOf(BizException.class)
                .extracting(e -> ((BizException) e).getCode())
                .isEqualTo(ErrorCode.SHOP_SUSPENDED);
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM product", Long.class)).isZero();
    }

    @Test
    void withoutDepositCannotCreateApproveOrRelistButCanTakeDownExistingProduct() {
        String seller = "U-LEGACY";
        CreateSkuRequest sku = new CreateSkuRequest();
        sku.setSpecName("默认规格");
        sku.setPrice(1000L);
        sku.setStockAvailable(1);
        CreateProductRequest request = new CreateProductRequest();
        request.setTitle("原有商品");
        request.setCategoryId("C04");
        request.setSkus(List.of(sku));
        var existing = productService.create(seller, request);
        var draft = productService.create(seller, request);
        AuditProductRequest approve = new AuditProductRequest();
        approve.setApproved(true);
        productService.audit(existing.getProductId(), "U-ADMIN", approve);

        // An active store may continue existing trade without permission to publish new items.
        when(shopClient.publication(seller))
                .thenReturn(Result.success(new ShopClient.PublicationEligibility(false, "保证金未登记")));
        assertThatThrownBy(() -> productService.create(seller, request)).isInstanceOf(BizException.class);
        assertThatThrownBy(() -> productService.audit(draft.getProductId(), "U-ADMIN", approve))
                .isInstanceOf(BizException.class);
        var status = new com.mall.product.dto.request.UpdateStatusRequest();
        status.setStatus("OFF_SHELF");
        assertThat(productService.updateStatus(existing.getProductId(), seller, status).getStatus())
                .isEqualTo(ProductStatus.OFF_SHELF);
        status.setStatus("ON_SALE");
        assertThatThrownBy(() -> productService.updateStatus(existing.getProductId(), seller, status))
                .isInstanceOf(BizException.class);
    }

    @Test
    void createAuditListAndManageSkus() {
        CreateSkuRequest firstSku = new CreateSkuRequest();
        firstSku.setSpecName("黑色 / M");
        firstSku.setPrice(9900L);
        firstSku.setStockAvailable(10);
        CreateSkuRequest secondSku = new CreateSkuRequest();
        secondSku.setSpecName("白色 / L");
        secondSku.setPrice(10900L);
        secondSku.setStockAvailable(20);

        CreateProductRequest create = new CreateProductRequest();
        create.setTitle("云原生实战 T 恤");
        create.setSubtitle("课程项目演示商品");
        create.setCategoryId("C04");
        create.setSkus(List.of(firstSku, secondSku));

        ProductDetailResponse created = productService.create("U-SELLER-1", create);
        assertThat(created.getStatus()).isEqualTo(ProductStatus.PENDING);
        assertThat(created.getPrice()).isEqualTo(9900L);
        assertThat(created.getSkus()).hasSize(2);

        AuditProductRequest audit = new AuditProductRequest();
        audit.setApproved(true);
        ProductDetailResponse audited = productService.audit(created.getProductId(), "U-ADMIN", audit);
        assertThat(audited.getStatus()).isEqualTo(ProductStatus.ON_SALE);

        var page = productService.list(null, null, null, null, "price_asc", null, null, 1, 20);
        assertThat(page.list()).hasSize(1);
        assertThat(page.list().get(0).getProductId()).isEqualTo(created.getProductId());

        String skuId = created.getSkus().get(0).getSkuId();
        UpdateStockRequest stock = new UpdateStockRequest();
        stock.setStockAvailable(15);
        assertThat(productService.updateStock(skuId, "U-SELLER-1", stock).getStockAvailable()).isEqualTo(15);

        CreateSkuRequest duplicate = new CreateSkuRequest();
        duplicate.setSpecName("黑色 / M");
        duplicate.setPrice(8800L);
        duplicate.setStockAvailable(3);
        assertThatThrownBy(() -> productService.addSku(created.getProductId(), "U-SELLER-1", duplicate))
                .isInstanceOf(BizException.class)
                .extracting(e -> ((BizException) e).getCode())
                .isEqualTo(ErrorCode.SPEC_CONFLICT);

        CreateSkuRequest third = new CreateSkuRequest();
        third.setSpecName("蓝色 / XL");
        third.setPrice(11900L);
        third.setStockAvailable(8);
        productService.addSku(created.getProductId(), "U-SELLER-1", third);
        assertThat(skuMapper.selectByProductId(created.getProductId())).hasSize(3);
    }

    /**
     * 待审核商品不能被普通用户看到，但管理员能按状态查出来（商品审核页依赖该能力）。
     */
    @Test
    void adminCanListPendingProductsByStatus() {
        CreateSkuRequest sku = new CreateSkuRequest();
        sku.setSpecName("默认规格");
        sku.setPrice(5000L);
        sku.setStockAvailable(5);

        CreateProductRequest create = new CreateProductRequest();
        create.setTitle("待审核商品");
        create.setCategoryId("C04");
        create.setSkus(List.of(sku));
        ProductDetailResponse created = productService.create("U-SELLER-2", create);

        // 普通用户（无角色）即使传了 status 也走公开列表，拿不到待审核商品
        var publicPage = productService.list(null, null, null, null, "default", "PENDING", null, 1, 20);
        assertThat(publicPage.list()).isEmpty();

        // 管理员按 PENDING 筛选能拿到
        var adminPage = productService.list(null, null, null, null, "default", "PENDING", "ADMIN", 1, 20);
        assertThat(adminPage.total()).isEqualTo(1);
        assertThat(adminPage.list().get(0).getProductId()).isEqualTo(created.getProductId());

        // 状态非法时拒绝
        assertThatThrownBy(() -> productService.list(null, null, null, null, "default", "UNKNOWN", "ADMIN", 1, 20))
                .isInstanceOf(BizException.class)
                .extracting(e -> ((BizException) e).getCode())
                .isEqualTo(ErrorCode.PARAM_INVALID);
    }

    @Test
    void adminCanManageEmptyCategoriesAndCannotDeleteCategoryWithProducts() {
        CreateCategoryRequest create = new CreateCategoryRequest();
        create.setName("临时分类");
        create.setSortNo(99);
        var category = productService.createCategory("U-ADMIN", create);
        assertThat(category.getName()).isEqualTo("临时分类");

        UpdateCategoryRequest update = new UpdateCategoryRequest();
        update.setName("临时分类-改名");
        update.setSortNo(3);
        assertThat(productService.updateCategory(category.getCategoryId(), "U-ADMIN", update).getName())
                .isEqualTo("临时分类-改名");
        assertThat(productService.sortCategory(category.getCategoryId(), "U-ADMIN", 1).getSortNo())
                .isEqualTo(1);
        productService.deleteCategory(category.getCategoryId(), "U-ADMIN");
        assertThat(productService.categories()).noneMatch(item -> item.getCategoryId().equals(category.getCategoryId()));

        CreateProductRequest product = new CreateProductRequest();
        product.setTitle("分类删除保护商品");
        product.setCategoryId("C04");
        CreateSkuRequest sku = new CreateSkuRequest();
        sku.setSpecName("默认");
        sku.setPrice(1000L);
        sku.setStockAvailable(1);
        product.setSkus(List.of(sku));
        ProductDetailResponse created = productService.create("U-SELLER-DELETE", product);
        assertThatThrownBy(() -> productService.deleteCategory("C04", "U-ADMIN"))
                .isInstanceOf(BizException.class)
                .extracting(e -> ((BizException) e).getCode())
                .isEqualTo(ErrorCode.PARAM_INVALID);
        productService.delete(created.getProductId(), "U-SELLER-DELETE");
    }
}
