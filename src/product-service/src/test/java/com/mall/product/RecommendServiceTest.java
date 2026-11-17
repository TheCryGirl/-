package com.mall.product;

import com.mall.product.client.PurchaseHistoryClient;
import com.mall.product.client.UserBehaviorClient;
import com.mall.product.common.Result;
import com.mall.product.dto.response.ProductListItem;
import com.mall.product.dto.response.RecommendResponse;
import com.mall.product.service.RecommendService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * 个性化推荐验收（#141）：冷启动兜底、排除已购买与失效商品、行为驱动的分类偏好。
 *
 * <p>两个行为接口都用 Mock 替换：本用例只验证推荐策略本身，不联调 user / order 服务。
 */
@SpringBootTest
@ActiveProfiles("test")
class RecommendServiceTest {

    @Autowired
    private RecommendService recommendService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @MockBean
    private UserBehaviorClient userBehaviorClient;

    @MockBean
    private PurchaseHistoryClient purchaseHistoryClient;

    @BeforeEach
    void clean() {
        jdbcTemplate.update("DELETE FROM stock_reservation_item");
        jdbcTemplate.update("DELETE FROM stock_reservation");
        jdbcTemplate.update("DELETE FROM sku");
        jdbcTemplate.update("DELETE FROM product");
        when(userBehaviorClient.recentBrowseHistory(anyString(), anyInt())).thenReturn(Result.success(List.of()));
        when(purchaseHistoryClient.recentPurchases(anyString(), anyInt())).thenReturn(Result.success(List.of()));
    }

    @Test
    void newUserGetsHotProductsInsteadOfEmptyList() {
        insertProduct("H1", "热销冠军", "C02", 9900L, 10, 500);
        insertProduct("H2", "热销亚军", "C03", 8800L, 10, 300);

        RecommendResponse response = recommendService.recommend("HOME", null, "U-NEW", 8);

        assertThat(response.getColdStart()).isTrue();
        assertThat(response.getBasis()).containsExactly("全站热销");
        assertThat(response.getItems()).extracting(ProductListItem::getProductId).containsExactly("H1", "H2");
    }

    @Test
    void purchasedProductsAreNeverRecommended() {
        insertProduct("BOUGHT", "已经买过的商品", "C02", 9900L, 10, 999);
        insertProduct("OTHER", "没买过的商品", "C02", 8900L, 10, 10);
        when(purchaseHistoryClient.recentPurchases(anyString(), anyInt()))
                .thenReturn(Result.success(List.of("BOUGHT")));

        RecommendResponse response = recommendService.recommend("HOME", null, "U-BUYER", 8);

        assertThat(response.getColdStart()).isFalse();
        assertThat(response.getItems()).extracting(ProductListItem::getProductId)
                .doesNotContain("BOUGHT")
                .contains("OTHER");
    }

    @Test
    void browseHistoryDrivesCategoryPreference() {
        String browsed = insertProduct("BR-1", "浏览过的耳机", "C02", 9900L, 10, 5);
        String sameCategory = insertProduct("BR-2", "同分类热销耳机", "C02", 12900L, 10, 400);
        String otherCategory = insertProduct("BR-3", "别的分类热销书", "C01", 3000L, 10, 900);
        when(userBehaviorClient.recentBrowseHistory(anyString(), anyInt()))
                .thenReturn(Result.success(List.of(browsed, browsed)));

        RecommendResponse response = recommendService.recommend("HOME", null, "U-BROWSER", 8);

        assertThat(response.getColdStart()).isFalse();
        assertThat(response.getBasis()).contains("数码电子");
        // 同分类优先，剩下名额才由全站热销补齐
        assertThat(response.getItems().get(0).getProductId()).isEqualTo(sameCategory);
        assertThat(response.getItems()).extracting(ProductListItem::getProductId)
                .contains(sameCategory, otherCategory);
    }

    @Test
    void detailScenePrefersSameCategoryAndExcludesItself() {
        insertProduct("D-CUR", "当前商品", "C03", 5000L, 10, 50);
        insertProduct("D-SAME", "同类商品", "C03", 6000L, 10, 20);
        insertProduct("D-HOT", "全站热销", "C02", 7000L, 10, 9000);

        RecommendResponse response = recommendService.recommend("DETAIL", "D-CUR", "U-ANY", 8);

        assertThat(response.getScene()).isEqualTo("DETAIL");
        assertThat(response.getItems()).extracting(ProductListItem::getProductId)
                .contains("D-SAME")
                .doesNotContain("D-CUR");
        assertThat(response.getItems().get(0).getProductId()).isEqualTo("D-SAME");
    }

    @Test
    void offShelfAndSoldOutProductsAreExcluded() {
        insertProduct("OK-1", "正常在售", "C02", 5000L, 5, 100);
        insertProductWithStatus("OFF-1", "已下架商品", "C02", 4000L, 5, 900, "OFF_SHELF");
        insertProductWithStatus("ZERO-1", "库存为0商品", "C02", 3000L, 0, 800, "ON_SALE");

        RecommendResponse response = recommendService.recommend("HOME", null, "U-NEW", 8);

        assertThat(response.getItems()).extracting(ProductListItem::getProductId).containsExactly("OK-1");
    }

    /**
     * 验收要求：推荐不超过 300ms。先预热一次，再连测 3 次取最坏值。
     *
     * <p>行为接口在这里是 Mock，测的是「取行为 → 归纳分类 → 查推荐 → 补位」这条本地链路的耗时。
     */
    @Test
    void recommendStaysUnder300msWith1000Products() {
        for (int i = 0; i < 1000; i++) {
            insertProduct("R" + i, "推荐商品 " + i, i % 2 == 0 ? "C02" : "C03", 1000L + i, 5, i);
        }
        when(userBehaviorClient.recentBrowseHistory(anyString(), anyInt()))
                .thenReturn(Result.success(List.of("R0", "R2", "R4")));

        recommendService.recommend("HOME", null, "U-PERF", 8);
        long worst = 0;
        for (int round = 0; round < 3; round++) {
            long start = System.nanoTime();
            RecommendResponse response = recommendService.recommend("HOME", null, "U-PERF", 8);
            long elapsedMs = (System.nanoTime() - start) / 1_000_000;
            assertThat(response.getItems()).hasSize(8);
            worst = Math.max(worst, elapsedMs);
        }
        System.out.println("[#141] 1000 条商品推荐最坏耗时: " + worst + "ms");
        assertThat(worst).isLessThan(300L);
    }

    /* ---------------- 造数 ---------------- */

    private String insertProduct(String productId, String title, String categoryId, long price, int stock,
                                 long sales) {
        return insertProductWithStatus(productId, title, categoryId, price, stock, sales, "ON_SALE");
    }

    private String insertProductWithStatus(String productId, String title, String categoryId, long price,
                                           int stock, long sales, String status) {
        Timestamp now = Timestamp.valueOf(LocalDateTime.now());
        jdbcTemplate.update("""
                INSERT INTO product(product_id, seller_id, title, subtitle, main_image, category_id, status,
                                    sales_count, created_at, updated_at, is_deleted)
                VALUES (?, 'U-SELLER', ?, NULL, NULL, ?, ?, ?, ?, ?, 0)
                """, productId, title, categoryId, status, sales, now, now);
        jdbcTemplate.update("""
                INSERT INTO sku(sku_id, product_id, spec_name, price, stock_available, stock_locked, version,
                                created_at, updated_at, is_deleted)
                VALUES (?, ?, '默认规格', ?, ?, 0, 0, ?, ?, 0)
                """, "SKU-" + productId, productId, price, stock, now, now);
        return productId;
    }
}
