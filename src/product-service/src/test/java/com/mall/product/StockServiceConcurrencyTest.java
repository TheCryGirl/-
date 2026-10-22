package com.mall.product;

import com.mall.product.common.BizException;
import com.mall.product.common.ErrorCode;
import com.mall.product.common.ProductStatus;
import com.mall.product.common.ReservationStatus;
import com.mall.product.common.TimeUtils;
import com.mall.product.dto.request.ReserveItem;
import com.mall.product.dto.request.ReserveStockRequest;
import com.mall.product.entity.Product;
import com.mall.product.entity.Sku;
import com.mall.product.mapper.ProductMapper;
import com.mall.product.mapper.SkuMapper;
import com.mall.product.service.StockService;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
class StockServiceConcurrencyTest {

    private static final String SKU_ID = "S-TEST-1";

    @Autowired
    private StockService stockService;

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

        LocalDateTime now = LocalDateTime.now();
        productMapper.insert(Product.builder()
                .productId("P-TEST-1").sellerId("U-SELLER-1").title("并发测试商品")
                .categoryId("C01").status(ProductStatus.ON_SALE).salesCount(0L)
                .createdAt(now).updatedAt(now).createdBy("U-SELLER-1").updatedBy("U-SELLER-1").isDeleted(0)
                .build());
        skuMapper.insert(Sku.builder()
                .skuId(SKU_ID).productId("P-TEST-1").specName("默认规格")
                .price(9900L).stockAvailable(10).stockLocked(0).version(0)
                .createdAt(now).updatedAt(now).isDeleted(0)
                .build());
    }

    @Test
    void concurrentReserveNeverOversells() throws Exception {
        int attempts = 30;
        ExecutorService pool = Executors.newFixedThreadPool(attempts);
        CountDownLatch ready = new CountDownLatch(attempts);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Boolean>> futures = new ArrayList<>();

        try {
            for (int i = 0; i < attempts; i++) {
                final int index = i;
                futures.add(pool.submit(() -> {
                    ready.countDown();
                    start.await(5, TimeUnit.SECONDS);
                    try {
                        stockService.reserve(request("O-CONCURRENT-" + index, 1));
                        return true;
                    } catch (BizException e) {
                        if (e.getCode() != ErrorCode.STOCK_INSUFFICIENT) {
                            throw e;
                        }
                        return false;
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
            assertThat(success).isEqualTo(10);
        } finally {
            pool.shutdownNow();
        }

        Sku sku = skuMapper.selectById(SKU_ID);
        assertThat(sku.getStockAvailable()).isZero();
        assertThat(sku.getStockLocked()).isEqualTo(10);
    }

    @Test
    void reserveReleaseAndConfirmFollowReservationStateMachine() {
        var first = stockService.reserve(request("O-IDEMPOTENT", 2));
        var second = stockService.reserve(request("O-IDEMPOTENT", 2));

        assertThat(second.getReservationId()).isEqualTo(first.getReservationId());
        Sku reserved = skuMapper.selectById(SKU_ID);
        assertThat(reserved.getStockAvailable()).isEqualTo(8);
        assertThat(reserved.getStockLocked()).isEqualTo(2);

        assertThat(stockService.release("O-IDEMPOTENT").released()).isTrue();
        assertThat(stockService.release("O-IDEMPOTENT").released()).isTrue();
        Sku released = skuMapper.selectById(SKU_ID);
        assertThat(released.getStockAvailable()).isEqualTo(10);
        assertThat(released.getStockLocked()).isZero();

        stockService.reserve(request("O-CONFIRM", 4));
        assertThat(stockService.confirm("O-CONFIRM").confirmed()).isTrue();
        assertThat(stockService.confirm("O-CONFIRM").confirmed()).isTrue();
        Sku confirmed = skuMapper.selectById(SKU_ID);
        assertThat(confirmed.getStockAvailable()).isEqualTo(6);
        assertThat(confirmed.getStockLocked()).isZero();

        assertThatThrownBy(() -> stockService.release("O-CONFIRM"))
                .isInstanceOf(BizException.class)
                .extracting(e -> ((BizException) e).getCode())
                .isEqualTo(ErrorCode.RESERVATION_NOT_FOUND);
    }

    private static ReserveStockRequest request(String orderId, int quantity) {
        ReserveItem item = new ReserveItem();
        item.setSkuId(SKU_ID);
        item.setQuantity(quantity);
        ReserveStockRequest request = new ReserveStockRequest();
        request.setOrderId(orderId);
        request.setExpireAt(TimeUtils.format(LocalDateTime.now().plusMinutes(30)));
        request.setItems(List.of(item));
        return request;
    }
}
