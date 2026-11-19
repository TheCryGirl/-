package com.mall.product;

import com.mall.product.common.BizException;
import com.mall.product.common.ErrorCode;
import com.mall.product.common.ProductStatus;
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
        jdbcTemplate.update("DELETE FROM stock_reservation_item");
        jdbcTemplate.update("DELETE FROM stock_reservation");
        jdbcTemplate.update("DELETE FROM sku");
        jdbcTemplate.update("DELETE FROM product");

        LocalDateTime now = LocalDateTime.now();
        productMapper.insert(Product.builder()
                .productId(PRODUCT_ID).sellerId("U-SELLER-FS").title("秒杀测试商品")
                .categoryId("C01").status(ProductStatus.ON_SALE).salesCount(0L)
                .createdAt(now).updatedAt(now).createdBy("U-SELLER-FS").updatedBy("U-SELLER-FS").isDeleted(0)
                .build());
        jdbcTemplate.update("INSERT INTO sku (sku_id,product_id,spec_name,price,stock_available,created_at,updated_at)"
                + " VALUES (?,?,?,12000,20,?,?)", SKU_ID, PRODUCT_ID, "标准款", now, now);
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

    @Autowired
    private com.mall.product.service.StockService stockService;

    @Test
    void formalReservationConsumesBothStocksAndCancellationRestoresEachOnce() {
        var request = formal("FORMAL-A", "U-FS-1", 9900);
        var first = stockService.reserve(request);
        assertThat(stockService.reserve(request).getReservationId()).isEqualTo(first.getReservationId());
        assertThat(stock(SALE_ID)).isEqualTo(9);
        assertThat(skuStock()).isEqualTo(19);
        assertThatThrownBy(() -> stockService.reserve(formal("FORMAL-B", "U-FS-1", 9900)))
                .isInstanceOf(BizException.class)
                .extracting(e -> ((BizException) e).getCode()).isEqualTo(ErrorCode.FLASH_SALE_LIMIT_EXCEEDED);
        stockService.release("FORMAL-A");
        stockService.release("FORMAL-A");
        assertThat(stock(SALE_ID)).isEqualTo(10);
        assertThat(skuStock()).isEqualTo(20);
        stockService.reserve(formal("FORMAL-C", "U-FS-1", 9900));
        assertThat(stock(SALE_ID)).isEqualTo(9);
    }

    @org.springframework.boot.test.mock.mockito.MockBean
    private com.mall.product.client.PurchaseHistoryClient orderClient;

    @Test
    void expiredHoldIsReleasedOnlyAfterOrderServiceClosesOrder() {
        stockService.reserve(formal("FORMAL-EXP", "U-FS-1", 9900));
        jdbcTemplate.update("UPDATE stock_reservation SET expire_at=? WHERE order_id='FORMAL-EXP'",
                LocalDateTime.now().minusMinutes(1));
        org.mockito.Mockito.when(orderClient.expireFlashSale("FORMAL-EXP"))
                .thenReturn(com.mall.product.common.Result.success(false));
        stockService.releaseExpiredReservations();
        assertThat(stock(SALE_ID)).isEqualTo(9);
        assertThat(skuStock()).isEqualTo(19);

        org.mockito.Mockito.when(orderClient.expireFlashSale("FORMAL-EXP"))
                .thenReturn(com.mall.product.common.Result.success(true));
        stockService.releaseExpiredReservations();
        stockService.releaseExpiredReservations();
        assertThat(stock(SALE_ID)).isEqualTo(10);
        assertThat(skuStock()).isEqualTo(20);
    }

    @Test
    void failedSkuReservationRollsBackActivityAndLimitRecord() {
        jdbcTemplate.update("UPDATE sku SET stock_available=0 WHERE sku_id=?", SKU_ID);
        assertThatThrownBy(() -> stockService.reserve(formal("FORMAL-A", "U-FS-1", 9900)))
                .isInstanceOf(BizException.class);
        assertThat(stock(SALE_ID)).isEqualTo(10);
        assertThat(count("SELECT COUNT(*) FROM flash_sale_order")).isZero();
        assertThat(count("SELECT COUNT(*) FROM stock_reservation")).isZero();
    }

    @Test
    void stalePriceDoesNotReserveAndPaidRefundKeepsActivityEligibilityConsumed() {
        assertThatThrownBy(() -> stockService.reserve(formal("FORMAL-A", "U-FS-1", 9800)))
                .isInstanceOf(BizException.class);
        assertThat(stock(SALE_ID)).isEqualTo(10);
        assertThat(skuStock()).isEqualTo(20);
        stockService.reserve(formal("FORMAL-B", "U-FS-1", 9900));
        stockService.confirm("FORMAL-B");
        stockService.refundRestock("FORMAL-B");
        assertThat(skuStock()).isEqualTo(20);
        assertThat(stock(SALE_ID)).isEqualTo(9);
        assertThat(count("SELECT COUNT(*) FROM flash_sale_order")).isEqualTo(1);
    }

    private com.mall.product.dto.request.ReserveStockRequest formal(String order, String buyer, long price) {
        var mapper = new com.fasterxml.jackson.databind.ObjectMapper()
                .disable(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
        return mapper.convertValue(java.util.Map.of("orderId", order,
                "expireAt", com.mall.product.common.TimeUtils.format(LocalDateTime.now().plusMinutes(10)),
                "items", List.of(java.util.Map.of("skuId", SKU_ID, "quantity", 1)),
                "flashSale", java.util.Map.of("saleId", SALE_ID, "buyerId", buyer, "unitPrice", price)),
                com.mall.product.dto.request.ReserveStockRequest.class);
    }

    private int skuStock() {
        return jdbcTemplate.queryForObject("SELECT stock_available FROM sku WHERE sku_id=?", Integer.class, SKU_ID);
    }

    @Test
    void quoteDoesNotWriteAndRejectsUnknownExpiredOrOverLimitActivity() {
        var quote = flashSaleService.quote("U-FS-1", SALE_ID, 1);
        assertThat(quote.unitPrice()).isEqualTo(9900L);
        assertThat(quote.skuId()).isEqualTo(SKU_ID);
        assertThat(stock(SALE_ID)).isEqualTo(10);
        assertThat(count("SELECT COUNT(*) FROM flash_sale_order")).isZero();
        assertThatThrownBy(() -> flashSaleService.quote("U-FS-1", "FS-NOT-EXISTS", 1))
                .isInstanceOf(BizException.class).extracting(e -> ((BizException) e).getCode())
                .isEqualTo(ErrorCode.FLASH_SALE_NOT_FOUND);
        insertSale("FS-TEST-EXPIRED", 500L, 5, 1, LocalDateTime.now().minusDays(2), LocalDateTime.now().minusMinutes(1));
        assertThatThrownBy(() -> flashSaleService.quote("U-FS-1", "FS-TEST-EXPIRED", 1))
                .isInstanceOf(BizException.class).extracting(e -> ((BizException) e).getCode())
                .isEqualTo(ErrorCode.FLASH_SALE_SOLD_OUT);
        assertThatThrownBy(() -> flashSaleService.quote("U-FS-1", SALE_ID, 2))
                .isInstanceOf(BizException.class).extracting(e -> ((BizException) e).getCode())
                .isEqualTo(ErrorCode.FLASH_SALE_LIMIT_EXCEEDED);
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
                        stockService.reserve(formal(userId, userId, 9900));
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
