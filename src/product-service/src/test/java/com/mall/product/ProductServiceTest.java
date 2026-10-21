package com.mall.product;

import com.mall.product.common.BizException;
import com.mall.product.common.ErrorCode;
import com.mall.product.common.ProductStatus;
import com.mall.product.dto.request.AuditProductRequest;
import com.mall.product.dto.request.CreateProductRequest;
import com.mall.product.dto.request.CreateSkuRequest;
import com.mall.product.dto.request.UpdateStockRequest;
import com.mall.product.dto.response.ProductDetailResponse;
import com.mall.product.mapper.SkuMapper;
import com.mall.product.service.ProductService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
class ProductServiceTest {

    @Autowired
    private ProductService productService;

    @Autowired
    private SkuMapper skuMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void clean() {
        jdbcTemplate.update("DELETE FROM stock_reservation_item");
        jdbcTemplate.update("DELETE FROM stock_reservation");
        jdbcTemplate.update("DELETE FROM sku");
        jdbcTemplate.update("DELETE FROM product");
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

        var page = productService.list(null, null, null, null, "price_asc", 1, 20);
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
}
