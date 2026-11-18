package com.mall.product;

import com.mall.product.common.BizException;
import com.mall.product.common.ErrorCode;
import com.mall.product.common.ProductStatus;
import com.mall.product.dto.response.FlashSaleReserveResponse;
import com.mall.product.dto.response.FlashSaleResponse;
import com.mall.product.entity.Product;
import com.mall.product.mapper.ProductMapper;
import com.mall.product.service.FlashSaleService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * #140 限时秒杀集成测试。
 *
 * <p>覆盖验收：进行中活动列表与已抢光展示态；条件 UPDATE 扣库存；活动外/不存在/超限购被拒；
 * 同一用户重复下单被唯一键拦下且库存回滚；50 并发抢 10 件无超卖（实测并发数贴 PR）。
 */
@SpringBootTest
@ActiveProfiles("test")
class FlashSaleIntegrationTest {

    private static final String PRODUCT_ID = "P-FS-1";
    private static final String SKU_ID = "S-FS-1";
    private static final String SALE_ID = "FS-TEST-1";

    @Autowired
    private FlashSaleService flashSaleService;

    @Autowired
    private ProductMapper productMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() {
        jdbcTemplate.update("DELETE FROM flash_sale_order");
        jdbcTemplate.update("DELETE FROM flash_sale");
        jdbcTemplate.update("DELETE FROM sku");
        jdbcTemplate.update("DELETE FROM product");

        LocalDateTime now = LocalDateTime.now();
        productMapper.insert(Product.builder()
                .productId(PRODUCT_ID).sellerId("U-SELLER-FS").title("秒杀测试商品")
                .categoryId("C01").status(ProductStatus.ON_SALE).salesCount(0L)
                .createdAt(now).updatedAt(now).createdBy("U-SELLER-FS").updatedBy("U-SELLER-FS").isDeleted(0)
                .build());
        insertSale(SALE_ID, 9900L, 10, 1, now.minusHours(1), now.plusDays(1));
    }

    @Test
    void ongoingListsActiveSalesWithSoldOutFlag() {
        insertSale("FS-TEST-FUTURE", 500L, 5, 1,
                LocalDateTime.now().plusHours(1), LocalDateTime.now().plusDays(2));
        insertSale("FS-TEST-EMPTY", 500L, 0, 1,
                LocalDateTime.now().minusHours(1), LocalDateTime.now().plusDays(2));

        List<FlashSaleResponse> sales = flashSaleService.ongoing();
        assertThat(sales).extracting(FlashSaleResponse::getSaleId)
                .contains(SALE_ID, "FS-TEST-EMPTY")
                .doesNotContain("FS-TEST-FUTURE");
        FlashSaleResponse empty = sales.stream()
                .filter(sale -> "FS-TEST-EMPTY".equals(sale.getSaleId())).findFirst().orElseThrow();
        assertThat(empty.isSoldOut()).isTrue();
        FlashSaleResponse active = sales.stream()
                .filter(sale -> SALE_ID.equals(sale.getSaleId())).findFirst().orElseThrow();
        assertThat(active.isSoldOut()).isFalse();
        assertThat(active.getTitle()).isEqualTo("秒杀测试商品");
        assertThat(active.getSalePrice()).isEqualTo(9900L);
        assertThat(active.getEndAt()).isNotNull();
    }

    @Test
    void reserveDecrementsStockAndRecordsOrder() {
        FlashSaleReserveResponse reserved = flashSaleService.reserve("U-FS-1", SALE_ID, 1);

        assertThat(reserved.getSaleId()).isEqualTo(SALE_ID);
        assertThat(stock(SALE_ID)).isEqualTo(9);
        assertThat(count("SELECT COUNT(*) FROM flash_sale_order WHERE sale_id = '" + SALE_ID + "'")).isEqualTo(1);
    }

    @Test
    void reserveRejectsUnknownSaleAndOutOfWindow() {
        assertThatThrownBy(() -> flashSaleService.reserve("U-FS-1", "FS-NOT-EXISTS", 1))
                .isInstanceOf(BizException.class)
                .extracting(e -> ((BizException) e).getCode())
                .isEqualTo(ErrorCode.FLASH_SALE_NOT_FOUND);

        insertSale("FS-TEST-EXPIRED", 500L, 5, 1,
                LocalDateTime.now().minusDays(2), LocalDateTime.now().minusMinutes(1));
        assertThatThrownBy(() -> flashSaleService.reserve("U-FS-1", "FS-TEST-EXPIRED", 1))
                .isInstanceOf(BizException.class)
                .extracting(e -> ((BizException) e).getCode())
                .isEqualTo(ErrorCode.FLASH_SALE_SOLD_OUT);

        insertSale("FS-TEST-NOT-STARTED", 500L, 5, 1,
                LocalDateTime.now().plusHours(1), LocalDateTime.now().plusDays(2));
        assertThatThrownBy(() -> flashSaleService.reserve("U-FS-1", "FS-TEST-NOT-STARTED", 1))
                .isInstanceOf(BizException.class)
                .extracting(e -> ((BizException) e).getCode())
                .isEqualTo(ErrorCode.FLASH_SALE_SOLD_OUT);
    }

    @Test
    void reserveRejectsOverLimitQuantity() {
        assertThatThrownBy(() -> flashSaleService.reserve("U-FS-1", SALE_ID, 2))
                .isInstanceOf(BizException.class)
                .extracting(e -> ((BizException) e).getCode())
                .isEqualTo(ErrorCode.FLASH_SALE_LIMIT_EXCEEDED);
        assertThat(stock(SALE_ID)).isEqualTo(10);
    }

    @Test
    void sameUserSecondReserveIsRejectedAndStockRollsBack() {
        flashSaleService.reserve("U-FS-1", SALE_ID, 1);
        assertThat(stock(SALE_ID)).isEqualTo(9);

        assertThatThrownBy(() -> flashSaleService.reserve("U-FS-1", SALE_ID, 1))
                .isInstanceOf(BizException.class)
                .extracting(e -> ((BizException) e).getCode())
                .isEqualTo(ErrorCode.FLASH_SALE_LIMIT_EXCEEDED);
        // 第二次的扣库存随事务回滚
        assertThat(stock(SALE_ID)).isEqualTo(9);
    }

    @Test
    void fiftyConcurrentBuyersGrabTenItemsWithoutOversell() throws Exception {
        int buyers = 50;
        int stock = 10;
        ExecutorService pool = Executors.newFixedThreadPool(buyers);
        CountDownLatch ready = new CountDownLatch(buyers);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Boolean>> futures = new ArrayList<>();
        AtomicInteger soldOut = new AtomicInteger();

        try {
            for (int i = 0; i < buyers; i++) {
                final String userId = "U-FS-RACE-" + i;
                futures.add(pool.submit(() -> {
                    ready.countDown();
                    start.await(5, TimeUnit.SECONDS);
                    try {
                        flashSaleService.reserve(userId, SALE_ID, 1);
                        return true;
                    } catch (BizException e) {
                        if (e.getCode() == ErrorCode.FLASH_SALE_SOLD_OUT) {
                            soldOut.incrementAndGet();
                            return false;
                        }
                        throw e;
                    }
                }));
            }
            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();

            int success = 0;
            for (Future<Boolean> future : futures) {
                if (Boolean.TRUE.equals(future.get(20, TimeUnit.SECONDS))) {
                    success++;
                }
            }
            assertThat(success).isEqualTo(stock);
            assertThat(soldOut.get()).isEqualTo(buyers - stock);
        } finally {
            pool.shutdownNow();
        }

        assertThat(stock(SALE_ID)).isZero();
        assertThat(count("SELECT COUNT(*) FROM flash_sale_order WHERE sale_id = '" + SALE_ID + "'"))
                .isEqualTo(stock);
    }

    private void insertSale(String saleId, long salePrice, int saleStock, int limitPerUser,
                            LocalDateTime startAt, LocalDateTime endAt) {
        jdbcTemplate.update("INSERT INTO flash_sale (sale_id, product_id, sku_id, sale_price, "
                        + "sale_stock, limit_per_user, start_at, end_at, is_deleted) VALUES (?, ?, ?, ?, ?, ?, ?, ?, 0)",
                saleId, PRODUCT_ID, SKU_ID, salePrice, saleStock, limitPerUser, startAt, endAt);
    }

    private int stock(String saleId) {
        Integer value = jdbcTemplate.queryForObject(
                "SELECT sale_stock FROM flash_sale WHERE sale_id = ?", Integer.class, saleId);
        return value == null ? 0 : value;
    }

    private int count(String sql) {
        Integer value = jdbcTemplate.queryForObject(sql, Integer.class);
        return value == null ? 0 : value;
    }
}
