package com.mall.product;

import com.mall.product.common.BizException;
import com.mall.product.common.ErrorCode;
import com.mall.product.common.ProductStatus;
import com.mall.product.common.TimeUtils;
import com.mall.product.dto.request.ReserveItem;
import com.mall.product.dto.request.ReserveStockRequest;
import com.mall.product.entity.Product;
import com.mall.product.entity.Sku;
import com.mall.product.mapper.ProductMapper;
import com.mall.product.mapper.SkuMapper;
import com.mall.product.service.StockService;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
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

/**
 * Issue #48: 100 件库存分别接受 100 和 500 个并发预占请求。
 * 测试只使用 H2 隔离库，测试结束后不会修改开发数据库。
 */
@SpringBootTest
@ActiveProfiles("test")
class Issue48InventoryConcurrencyTest {

    private static final String SKU_ID = "S-ISSUE48";
    private static final int STOCK = 100;

    @Autowired
    private StockService stockService;

    @Autowired
    private ProductMapper productMapper;

    @Autowired
    private SkuMapper skuMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @ParameterizedTest(name = "stock=100, concurrent requests={0}")
    @ValueSource(ints = {100, 500})
    void reservationsNeverExceedStock(int attempts) throws Exception {
        resetFixture();
        ExecutorService pool = Executors.newFixedThreadPool(Math.min(attempts, 100));
        CountDownLatch ready = new CountDownLatch(Math.min(attempts, 100));
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Boolean>> futures = new ArrayList<>(attempts);

        try {
            for (int i = 0; i < attempts; i++) {
                final int index = i;
                futures.add(pool.submit(() -> {
                    ready.countDown();
                    assertThat(start.await(30, TimeUnit.SECONDS)).isTrue();
                    try {
                        stockService.reserve(request("O-ISSUE48-" + attempts + "-" + index));
                        return true;
                    } catch (BizException e) {
                        if (e.getCode() != ErrorCode.STOCK_INSUFFICIENT) {
                            throw e;
                        }
                        return false;
                    }
                }));
            }
            assertThat(ready.await(30, TimeUnit.SECONDS)).isTrue();
            start.countDown();

            long success = 0;
            for (Future<Boolean> future : futures) {
                if (future.get(120, TimeUnit.SECONDS)) {
                    success++;
                }
            }
            assertThat(success).isEqualTo(STOCK);
        } finally {
            pool.shutdownNow();
        }

        Sku sku = skuMapper.selectById(SKU_ID);
        assertThat(sku.getStockAvailable()).isZero();
        assertThat(sku.getStockLocked()).isEqualTo(STOCK);
        assertThat(sku.getStockAvailable()).isGreaterThanOrEqualTo(0);
    }

    private void resetFixture() {
        jdbcTemplate.update("DELETE FROM stock_reservation_item");
        jdbcTemplate.update("DELETE FROM stock_reservation");
        jdbcTemplate.update("DELETE FROM sku");
        jdbcTemplate.update("DELETE FROM product");
        LocalDateTime now = LocalDateTime.now();
        productMapper.insert(Product.builder().productId("P-ISSUE48").sellerId("SELLER-48")
                .title("Issue 48 concurrency fixture").categoryId("C01").status(ProductStatus.ON_SALE)
                .salesCount(0L).createdAt(now).updatedAt(now).createdBy("SELLER-48")
                .updatedBy("SELLER-48").isDeleted(0).build());
        skuMapper.insert(Sku.builder().skuId(SKU_ID).productId("P-ISSUE48").specName("default")
                .price(100L).stockAvailable(STOCK).stockLocked(0).version(0)
                .createdAt(now).updatedAt(now).isDeleted(0).build());
    }

    private static ReserveStockRequest request(String orderId) {
        ReserveItem item = new ReserveItem();
        item.setSkuId(SKU_ID);
        item.setQuantity(1);
        ReserveStockRequest request = new ReserveStockRequest();
        request.setOrderId(orderId);
        request.setExpireAt(TimeUtils.format(LocalDateTime.now().plusMinutes(30)));
        request.setItems(List.of(item));
        return request;
    }
}

