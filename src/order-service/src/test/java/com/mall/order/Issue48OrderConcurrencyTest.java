package com.mall.order;

import com.mall.order.client.CartClient;
import com.mall.order.client.ProductClient;
import com.mall.order.client.UserClient;
import com.mall.order.client.dto.AddressDTO;
import com.mall.order.client.dto.CartDTO;
import com.mall.order.client.dto.CartItemDTO;
import com.mall.order.client.dto.ProductDetailDTO;
import com.mall.order.client.dto.ReleaseResponseDTO;
import com.mall.order.client.dto.ReserveItemDTO;
import com.mall.order.client.dto.ReserveStockRequestDTO;
import com.mall.order.client.dto.ReserveStockResponseDTO;
import com.mall.order.client.dto.SkuDTO;
import com.mall.order.common.OrderStatus;
import com.mall.order.common.Result;
import com.mall.order.dto.request.CreateOrderRequest;
import com.mall.order.dto.response.CreateOrderResponse;
import com.mall.order.mapper.OrderMapper;
import com.mall.order.service.OrderService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Issue #48: 验证取消/超时竞态和重复幂等键并发提交。
 * 测试使用 H2 隔离库与 Mock 下游，不修改开发数据库或生产实现。
 */
@SpringBootTest
@ActiveProfiles("test")
class Issue48OrderConcurrencyTest {

    private static final String BUYER = "U48-BUYER";
    private static final String SELLER = "U48-SELLER";

    @Autowired
    private OrderService orderService;

    @Autowired
    private OrderMapper orderMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @MockBean
    private CartClient cartClient;

    @MockBean
    private UserClient userClient;

    @MockBean
    private ProductClient productClient;

    @BeforeEach
    void setUp() {
        jdbcTemplate.update("DELETE FROM order_compensation");
        jdbcTemplate.update("DELETE FROM order_idempotency");
        jdbcTemplate.update("DELETE FROM order_item");
        jdbcTemplate.update("DELETE FROM `order`");

        AddressDTO address = new AddressDTO();
        address.setAddressId("A48");
        address.setReceiver("测试买家");
        address.setPhone("13800138000");
        address.setProvince("天津市");
        address.setCity("北辰区");
        address.setDistrict("北辰区");
        address.setDetail("实验楼");

        CartItemDTO item = new CartItemDTO();
        item.setCartItemId("I48");
        item.setSkuId("S48");
        item.setProductId("P48");
        item.setTitle("并发测试商品");
        item.setSpecName("默认");
        item.setQuantity(1);
        item.setSelected(true);
        item.setStockAvailable(100);
        item.setInvalid(false);
        CartDTO cart = new CartDTO();
        cart.setCartId("C48");
        cart.setItems(List.of(item));

        ProductDetailDTO product = new ProductDetailDTO();
        product.setProductId("P48");
        product.setSellerId(SELLER);
        product.setTitle("并发测试商品");
        product.setStatus("ON_SALE");
        SkuDTO sku = new SkuDTO();
        sku.setSkuId("S48");
        sku.setSpecName("默认");
        sku.setPrice(100L);
        sku.setStockAvailable(100);
        product.setSkus(List.of(sku));

        when(cartClient.getCart(anyString(), anyString())).thenReturn(Result.success(cart));
        when(cartClient.deleteItem(anyString(), anyString(), anyString())).thenReturn(Result.success());
        when(userClient.addresses(anyString(), anyString())).thenReturn(Result.success(List.of(address)));
        when(productClient.getProduct(anyString())).thenReturn(Result.success(product));
        when(productClient.reserve(anyString(), any(ReserveStockRequestDTO.class)))
                .thenReturn(Result.success(new ReserveStockResponseDTO("R48",
                        List.of(new ReserveItemDTO("S48", 1)))));
        when(productClient.release(anyString(), any(ProductClient.ReleaseRequest.class)))
                .thenReturn(Result.success(new ReleaseResponseDTO(true)));
    }

    @Test
    void buyerCancelAndTimeoutCancelReleaseInventoryOnlyOnce() throws Exception {
        CreateOrderResponse created = orderService.create(BUYER, "issue48-cancel-key", request());
        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            Future<?> buyerCancel = pool.submit(() -> {
                await(start);
                orderService.cancel(created.getOrderId(), BUYER);
            });
            Future<?> timeoutCancel = pool.submit(() -> {
                await(start);
                orderService.cancelTimeout(created.getOrderId());
            });
            start.countDown();
            buyerCancel.get(30, TimeUnit.SECONDS);
            timeoutCancel.get(30, TimeUnit.SECONDS);
        } finally {
            pool.shutdownNow();
        }

        assertThat(orderMapper.selectById(created.getOrderId()).getStatus()).isEqualTo(OrderStatus.CANCELLED);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT inventory_released FROM `order` WHERE order_id = ?", Integer.class, created.getOrderId()))
                .isEqualTo(1);
        verify(productClient, times(1)).release(anyString(), any(ProductClient.ReleaseRequest.class));
    }

    @Test
    void concurrentDuplicateIdempotencyKeyReturnsOneOrder() throws Exception {
        int attempts = 50;
        ExecutorService pool = Executors.newFixedThreadPool(20);
        CountDownLatch ready = new CountDownLatch(20);
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<CreateOrderResponse>> futures = java.util.stream.IntStream.range(0, attempts)
                    .mapToObj(index -> pool.submit(() -> {
                        ready.countDown();
                        assertThat(start.await(30, TimeUnit.SECONDS)).isTrue();
                        return orderService.create(BUYER, "issue48-idempotency-key", request());
                    })).toList();
            assertThat(ready.await(30, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            List<CreateOrderResponse> responses = futures.stream().map(Issue48OrderConcurrencyTest::get).toList();
            CreateOrderResponse first = responses.get(0);
            Set<String> orderIds = responses.stream().map(CreateOrderResponse::getOrderId).collect(Collectors.toSet());
            assertThat(orderIds).hasSize(1);
            assertThat(responses).allSatisfy(response -> {
                assertThat(response.getOrderNo()).isEqualTo(first.getOrderNo());
                assertThat(response.getPayAmount()).isEqualTo(first.getPayAmount());
                assertThat(response.getExpireAt()).isEqualTo(first.getExpireAt());
            });
        } finally {
            pool.shutdownNow();
        }

        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM `order` WHERE is_deleted = 0", Integer.class)).isEqualTo(1);
        verify(productClient, times(1)).reserve(anyString(), any(ReserveStockRequestDTO.class));
    }

    private static CreateOrderRequest request() {
        CreateOrderRequest request = new CreateOrderRequest();
        request.setAddressId("A48");
        request.setCartItemIds(List.of("I48"));
        return request;
    }

    private static void await(CountDownLatch start) {
        try {
            assertThat(start.await(30, TimeUnit.SECONDS)).isTrue();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }

    private static CreateOrderResponse get(Future<CreateOrderResponse> future) {
        try {
            return future.get(60, TimeUnit.SECONDS);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
