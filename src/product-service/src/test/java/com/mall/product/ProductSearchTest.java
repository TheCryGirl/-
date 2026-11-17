package com.mall.product;

import com.mall.product.common.PageResult;
import com.mall.product.dto.response.ProductListItem;
import com.mall.product.service.ProductService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 检索能力验收（#141）：多字段匹配、相关度排序、纠错提示、属性筛选与 1000 条量级耗时。
 *
 * <p>直接写库构造商品，避免依赖建品 / 审核链路，用例只看检索本身。
 */
@SpringBootTest
@ActiveProfiles("test")
class ProductSearchTest {

    @Autowired
    private ProductService productService;

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
    void searchMatchesTitleAndSubtitleAndRanksTitleHitFirst() {
        String titleHit = insertProduct("无线降噪耳机 主动降噪", null, "C02", 100000L, 10, 20, "默认规格");
        String subtitleHit = insertProduct("桌面收纳盒", "适配各类降噪耳机", "C03", 5000L, 10, 1, "默认规格");

        PageResult<ProductListItem> page = productService.list("降噪耳机", null, null, null, null,
                "default", null, null, 1, 20);

        assertThat(page.total()).isEqualTo(2);
        assertThat(page.list()).extracting(ProductListItem::getProductId)
                .containsExactly(titleHit, subtitleHit);
    }

    @Test
    void bigramTokenizationHitsContinuousChineseTitle() {
        String product = insertProduct("无线蓝牙耳机", null, "C02", 19900L, 10, 3, "默认规格");

        // 「蓝牙耳」在标题里是连续子串，二元分词后 蓝牙 / 牙耳 都能命中
        PageResult<ProductListItem> page = productService.list("蓝牙耳", null, null, null, null,
                "default", null, null, 1, 20);

        assertThat(page.list()).extracting(ProductListItem::getProductId).containsExactly(product);
    }

    @Test
    void skuSpecNameAndCategoryNameParticipateInMatching() {
        String specHit = insertProduct("保温杯", null, "C03", 8000L, 10, 1, "耳机收纳款");
        String categoryHit = insertProduct("普通商品A", null, "C02", 9000L, 10, 1, "默认规格");

        PageResult<ProductListItem> specPage = productService.list("耳机收纳", null, null, null, null,
                "default", null, null, 1, 20);
        assertThat(specPage.list()).extracting(ProductListItem::getProductId).contains(specHit);

        PageResult<ProductListItem> categoryPage = productService.list("数码", null, null, null, null,
                "default", null, null, 1, 20);
        assertThat(categoryPage.list()).extracting(ProductListItem::getProductId).contains(categoryHit);
    }

    @Test
    void typoOnlyAddsSuggestionAndNeverRewritesQuery() {
        String target = insertProduct("无线耳机", null, "C02", 15900L, 10, 5, "默认规格");
        String noisy = insertProduct("降噪耳机", null, "C02", 25900L, 10, 5, "默认规格");

        PageResult<ProductListItem> typoPage = productService.list("无线儿机", null, null, null, null,
                "default", null, null, 1, 20);

        // 纠错只提示：结果仍按原查询的词算（只命中「无线」这一段），不会替换成「无线耳机」或「耳机」
        assertThat(typoPage.suggestions()).contains("无线耳机");
        assertThat(typoPage.list()).extracting(ProductListItem::getProductId).containsExactly(target);
        assertThat(typoPage.list()).extracting(ProductListItem::getProductId).doesNotContain(noisy);

        // 正确输入时不提示纠错
        PageResult<ProductListItem> correctPage = productService.list("无线耳机", null, null, null, null,
                "default", null, null, 1, 20);
        assertThat(correctPage.suggestions()).isEmpty();
    }

    @Test
    void inStockFilterSwitchesBetweenAvailableAndSoldOut() {
        String available = insertProduct("耳机保护套", null, "C02", 3000L, 8, 1, "默认规格");
        String soldOut = insertProduct("耳机延长线", null, "C02", 2000L, 0, 1, "默认规格");

        PageResult<ProductListItem> defaultPage = productService.list("耳机", null, null, null, null,
                "default", null, null, 1, 20);
        assertThat(defaultPage.list()).extracting(ProductListItem::getProductId).containsExactly(available);

        PageResult<ProductListItem> soldOutPage = productService.list("耳机", null, null, null, false,
                "default", null, null, 1, 20);
        assertThat(soldOutPage.list()).extracting(ProductListItem::getProductId).containsExactly(soldOut);

        PageResult<ProductListItem> onlyAvailablePage = productService.list("耳机", null, null, null, true,
                "default", null, null, 1, 20);
        assertThat(onlyAvailablePage.list()).extracting(ProductListItem::getProductId).containsExactly(available);
    }

    /**
     * 验收要求：1000 条商品内检索不超过 300ms。
     *
     * <p>先跑一次预热（JIT + 连接池），再连续测 3 次取最大值，避免把冷启动算进耗时。
     */
    @Test
    void searchStaysUnder300msWith1000Products() {
        for (int i = 0; i < 1000; i++) {
            String title = i % 10 == 0 ? "降噪耳机 第" + i + " 款" : "普通商品 " + i;
            insertProductRaw("P" + i, "S" + i, title, "副标题 " + i, "C02", 1000L + i, 5, i, "默认规格");
        }
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM product", Long.class)).isEqualTo(1000L);

        productService.list("降噪耳机", null, null, null, null, "default", null, null, 1, 20);
        long worst = 0;
        for (int round = 0; round < 3; round++) {
            long start = System.nanoTime();
            PageResult<ProductListItem> page = productService.list("降噪耳机", null, null, null, null,
                    "default", null, null, 1, 20);
            long elapsedMs = (System.nanoTime() - start) / 1_000_000;
            assertThat(page.total()).isEqualTo(100);
            worst = Math.max(worst, elapsedMs);
        }
        System.out.println("[#141] 1000 条商品检索最坏耗时: " + worst + "ms");
        assertThat(worst).isLessThan(300L);
    }

    /* ---------------- 造数 ---------------- */

    private String insertProduct(String title, String subtitle, String categoryId, long price, int stock,
                                 long sales, String specName) {
        String productId = "P-" + UUID.randomUUID().toString().substring(0, 8);
        insertProductRaw(productId, "S-" + productId, title, subtitle, categoryId, price, stock, sales, specName);
        return productId;
    }

    private void insertProductRaw(String productId, String skuId, String title, String subtitle,
                                  String categoryId, long price, int stock, long sales, String specName) {
        Timestamp now = Timestamp.valueOf(LocalDateTime.now());
        jdbcTemplate.update("""
                INSERT INTO product(product_id, seller_id, title, subtitle, main_image, category_id, status,
                                    sales_count, created_at, updated_at, is_deleted)
                VALUES (?, ?, ?, ?, NULL, ?, 'ON_SALE', ?, ?, ?, 0)
                """, productId, "U-SELLER", title, subtitle, categoryId, sales, now, now);
        jdbcTemplate.update("""
                INSERT INTO sku(sku_id, product_id, spec_name, price, stock_available, stock_locked, version,
                                created_at, updated_at, is_deleted)
                VALUES (?, ?, ?, ?, ?, 0, 0, ?, ?, 0)
                """, skuId, productId, specName, price, stock, now, now);
    }

    /** 用例里偶尔需要断言集合，保留一个短辅助，避免重复写 stream。 */
    private static List<String> ids(PageResult<ProductListItem> page) {
        return page.list().stream().map(ProductListItem::getProductId).toList();
    }
}
