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
import com.mall.order.common.BizException;
import com.mall.order.common.ErrorCode;
import com.mall.order.common.OrderStatus;
import com.mall.order.common.Result;
import com.mall.order.dto.request.CreateOrderRequest;
import com.mall.order.dto.request.ShipOrderRequest;
import com.mall.order.dto.response.CreateOrderResponse;
import com.mall.order.dto.response.OrderDetailResponse;
import com.mall.order.mapper.OrderMapper;
import com.mall.order.service.OrderService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SpringBootTest
@ActiveProfiles("test")
class OrderServiceTest {

    private static final String BUYER = "U10001";
    private static final String SELLER = "U10002";
    private static final String ORDER_KEY = "idem-key-0001";

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
        address.setAddressId("A1");
        address.setReceiver("张三");
        address.setPhone("13800138000");
        address.setProvince("天津市");
        address.setCity("天津市");
        address.setDistrict("北辰区");
        address.setDetail("河北工业大学 3 号宿舍楼");
        address.setIsDefault(true);

        CartItemDTO item = new CartItemDTO();
        item.setCartItemId("I1");
        item.setSkuId("S1");
        item.setProductId("P1");
        item.setTitle("测试商品");
        item.setSpecName("默认规格");
        item.setMainImage("/images/test.png");
        item.setPrice(9900L);
        item.setQuantity(2);
        item.setSelected(true);
        item.setStockAvailable(10);
        item.setInvalid(false);

        CartItemDTO second = new CartItemDTO();
        second.setCartItemId("I2");
        second.setSkuId("S1");
        second.setProductId("P1");
        second.setQuantity(1);
        second.setSelected(true);
        second.setStockAvailable(10);
        second.setInvalid(false);

        CartDTO cart = new CartDTO();
        cart.setCartId("C1");
        cart.setItems(List.of(item, second));

        ProductDetailDTO product = new ProductDetailDTO();
        product.setProductId("P1");
        product.setSellerId(SELLER);
        product.setTitle("测试商品");
        product.setMainImage("/images/test.png");
        product.setStatus("ON_SALE");
        product.setPrice(9900L);
        SkuDTO sku = new SkuDTO();
        sku.setSkuId("S1");
        sku.setSpecName("默认规格");
        sku.setPrice(9900L);
        sku.setStockAvailable(10);
        product.setSkus(List.of(sku));

        when(cartClient.getCart(anyString(), anyString())).thenReturn(Result.success(cart));
        when(cartClient.deleteItem(anyString(), anyString(), anyString())).thenReturn(Result.success());
        when(userClient.addresses(anyString(), anyString())).thenReturn(Result.success(List.of(address)));
        when(productClient.getProduct(anyString())).thenReturn(Result.success(product));
        when(productClient.reserve(anyString(), any(ReserveStockRequestDTO.class)))
                .thenReturn(Result.success(new ReserveStockResponseDTO("R1",
                        List.of(new ReserveItemDTO("S1", 2)))));
        when(productClient.release(anyString(), any(ProductClient.ReleaseRequest.class)))
                .thenReturn(Result.success(new ReleaseResponseDTO(true)));
    }

    @Test
    void repeatedSubmitCreatesOnlyOneOrderAndOneReservation() {
        CreateOrderResponse first = orderService.create(BUYER, ORDER_KEY, request("I1"));
        CreateOrderResponse second = orderService.create(BUYER, ORDER_KEY, request("I1"));

        assertThat(second.getOrderId()).isEqualTo(first.getOrderId());
        assertThat(first.getPayAmount()).isEqualTo(19800L);
        assertThat(count("SELECT COUNT(*) FROM `order` WHERE is_deleted = 0")).isEqualTo(1);
        verify(productClient, times(1)).reserve(anyString(), any(ReserveStockRequestDTO.class));
    }

    @Test
    void sameKeyWithDifferentCartIsRejected() {
        orderService.create(BUYER, ORDER_KEY, request("I1"));

        assertThatThrownBy(() -> orderService.create(BUYER, ORDER_KEY, request("I2")))
                .isInstanceOf(BizException.class)
                .extracting(e -> ((BizException) e).getCode())
                .isEqualTo(ErrorCode.IDEMPOTENCY_CONFLICT);
    }

    @Test
    void reserveFailureSoftDeletesOrderAndKeepsIdempotencyRetryable() {
        when(productClient.reserve(anyString(), any(ReserveStockRequestDTO.class)))
                .thenReturn(Result.fail(ErrorCode.STOCK_INSUFFICIENT, "库存不足"));

        assertThatThrownBy(() -> orderService.create(BUYER, "idem-key-reserve-fail", request("I1")))
                .isInstanceOf(BizException.class)
                .extracting(e -> ((BizException) e).getCode())
                .isEqualTo(ErrorCode.STOCK_INSUFFICIENT);

        assertThat(count("SELECT COUNT(*) FROM `order` WHERE is_deleted = 1")).isEqualTo(1);
        // 预占失败后幂等记录不再删除，而是退回 FAILED 保留下来：
        // 既让同一幂等键可以重试，又保留 request_hash 以便拒绝「同 key 不同请求」。
        assertThat(count("SELECT COUNT(*) FROM order_idempotency WHERE status = 'FAILED'")).isEqualTo(1);
        assertThat(count("SELECT COUNT(*) FROM order_compensation")).isEqualTo(1);
    }

    @Test
    void cancelReleasesInventory() {
        CreateOrderResponse created = orderService.create(BUYER, "idem-key-cancel-01", request("I1"));

        OrderDetailResponse cancelled = orderService.cancel(created.getOrderId(), BUYER);

        assertThat(cancelled.getStatus()).isEqualTo(OrderStatus.CANCELLED);
        assertThat(count("SELECT inventory_released FROM `order` WHERE order_id = '"
                + created.getOrderId() + "'")).isEqualTo(1);
        verify(productClient, times(1)).release(anyString(), any(ProductClient.ReleaseRequest.class));
    }

    @Test
    void fullLifecycleAndTimeoutCancellation() {
        CreateOrderResponse created = orderService.create(BUYER, "idem-key-lifecycle-1", request("I1"));
        orderService.markPaid(created.getOrderId());
        assertThat(orderMapper.selectById(created.getOrderId()).getStatus()).isEqualTo(OrderStatus.PAID);

        ShipOrderRequest ship = new ShipOrderRequest();
        ship.setTrackingNo("SF123456");
        assertThat(orderService.ship(created.getOrderId(), SELLER, "SELLER", ship).getStatus())
                .isEqualTo(OrderStatus.SHIPPED);
        assertThat(orderService.confirm(created.getOrderId(), BUYER).getStatus())
                .isEqualTo(OrderStatus.COMPLETED);
        orderService.markRefunded(created.getOrderId());
        assertThat(orderMapper.selectById(created.getOrderId()).getStatus())
                .isEqualTo(OrderStatus.REFUNDED);
    }

    @Test
    void timeoutCancellationReleasesInventory() {
        CreateOrderResponse created = orderService.create(BUYER, "idem-key-timeout-01", request("I1"));
        jdbcTemplate.update("UPDATE `order` SET created_at = ? WHERE order_id = ?",
                LocalDateTime.now().minusMinutes(31), created.getOrderId());

        assertThat(orderMapper.selectTimeoutCandidates(LocalDateTime.now().minusMinutes(30), 10))
                .contains(created.getOrderId());
        assertThat(orderService.cancelTimeout(created.getOrderId())).isTrue();
        assertThat(orderMapper.selectById(created.getOrderId()).getStatus())
                .isEqualTo(OrderStatus.CANCELLED);
        verify(productClient, times(1)).release(anyString(), any(ProductClient.ReleaseRequest.class));
    }

    private static CreateOrderRequest request(String cartItemId) {
        CreateOrderRequest request = new CreateOrderRequest();
        request.setAddressId("A1");
        request.setCartItemIds(List.of(cartItemId));
        return request;
    }

    private int count(String sql) {
        Integer value = jdbcTemplate.queryForObject(sql, Integer.class);
        return value == null ? 0 : value;
    }
}
