package com.mall.product;

import com.mall.product.common.ProductStatus;
import com.mall.product.dto.request.UpdateLowStockThresholdRequest;
import com.mall.product.entity.Product;
import com.mall.product.entity.Sku;
import com.mall.product.mapper.ProductMapper;
import com.mall.product.mapper.SkuMapper;
import com.mall.product.service.StockAlertService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class StockAlertServiceTest {

    @Autowired
    private StockAlertService stockAlertService;

    @Autowired
    private ProductMapper productMapper;

    @Autowired
    private SkuMapper skuMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() {
        jdbcTemplate.update("DELETE FROM stock_reservation_item");
        jdbcTemplate.update("DELETE FROM stock_reservation");
        jdbcTemplate.update("DELETE FROM sku");
        jdbcTemplate.update("DELETE FROM product");
        jdbcTemplate.update("UPDATE product_setting SET setting_value = 5 WHERE setting_key = 'LOW_STOCK_THRESHOLD'");
        LocalDateTime now = LocalDateTime.now();
        productMapper.insert(Product.builder().productId("P-ALERT-1").sellerId("SELLER-A")
                .title("预警商品").categoryId("C01").status(ProductStatus.ON_SALE).salesCount(0L)
                .createdAt(now).updatedAt(now).createdBy("SELLER-A").updatedBy("SELLER-A").isDeleted(0).build());
        productMapper.insert(Product.builder().productId("P-ALERT-2").sellerId("SELLER-B")
                .title("其他商品").categoryId("C01").status(ProductStatus.ON_SALE).salesCount(0L)
                .createdAt(now).updatedAt(now).createdBy("SELLER-B").updatedBy("SELLER-B").isDeleted(0).build());
        skuMapper.insert(Sku.builder().skuId("S-ALERT-1").productId("P-ALERT-1").specName("默认")
                .price(1000L).stockAvailable(3).stockLocked(0).version(0).createdAt(now).updatedAt(now).isDeleted(0).build());
        skuMapper.insert(Sku.builder().skuId("S-ALERT-2").productId("P-ALERT-2").specName("默认")
                .price(1000L).stockAvailable(4).stockLocked(0).version(0).createdAt(now).updatedAt(now).isDeleted(0).build());
    }

    @Test
    void sellerSeesOwnAlertsAndThresholdOverrideTakesEffectImmediately() {
        assertThat(stockAlertService.alerts("SELLER-A", false)).extracting("skuId").containsExactly("S-ALERT-1");
        stockAlertService.updateSkuThreshold("S-ALERT-1", "SELLER-A", 2);
        assertThat(stockAlertService.alerts("SELLER-A", false)).isEmpty();
        assertThat(stockAlertService.alerts("SELLER-B", false)).extracting("skuId").containsExactly("S-ALERT-2");
    }

    @Test
    void globalThresholdImmediatelyChangesAdminAlerts() {
        assertThat(stockAlertService.alerts(null, true)).extracting("skuId")
                .containsExactlyInAnyOrder("S-ALERT-1", "S-ALERT-2");
        stockAlertService.updateGlobalThreshold(3);
        assertThat(stockAlertService.alerts(null, true)).isEmpty();
        stockAlertService.updateGlobalThreshold(5);
        assertThat(stockAlertService.alerts(null, true)).extracting("skuId")
                .containsExactlyInAnyOrder("S-ALERT-1", "S-ALERT-2");
    }
}

