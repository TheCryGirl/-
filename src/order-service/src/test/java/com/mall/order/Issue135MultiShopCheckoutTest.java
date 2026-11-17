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
import com.mall.order.client.dto.ShopBriefDTO;
import com.mall.order.client.dto.SkuDTO;
import com.mall.order.common.BizException;
import com.mall.order.common.ErrorCode;
import com.mall.order.common.OrderStatus;
import com.mall.order.common.Result;
import com.mall.order.dto.request.CreateOrderRequest;
import com.mall.order.dto.request.PreviewOrderRequest;
import com.mall.order.dto.request.ShipOrderRequest;
import com.mall.order.dto.response.CreateOrderResponse;
import com.mall.order.dto.response.MainOrderDetailResponse;
import com.mall.order.dto.response.OrderPreviewResponse;
import com.mall.order.entity.MainOrder;
import com.mall.order.entity.Order;
import com.mall.order.mapper.MainOrderMapper;
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
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Issue #135：多店铺合并结算。
 *
 * <p>覆盖验收清单：跨店一次支付生成 1 主订单 + 2 子订单、子订单分别发货/退款互不影响、
 * 主订单取消释放全部未支付子订单库存、单店不产生多余层级、历史单店订单兼容、
 * 同一购物车重复提交（含并发）只产生一个主订单。
 * 测试使用 H2 隔离库与 Mock 下游，不修改开发数据库。
 */
@SpringBootTest
@ActiveProfiles("test")
class Issue135MultiShopCheckoutTest {

    private static final String BUYER = "U135-BUYER";
    private static final String SELLER_A = "U135-SELLER-A";
    private static final String SELLER_B = "U135-SELLER-B";

    @Autowired
    private OrderService orderService;

    @Autowired
    private OrderMapper orderMapper;

    @Autowired
    private MainOrderMapper mainOrderMapper;

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
        jdbcTemplate.update("DELETE FROM main_order");

        AddressDTO address = new AddressDTO();
        address.setAddressId("A135");
        address.setReceiver("测试买家");
        address.setPhone("13800138000");
        address.setProvince("天津市");
        address.setCity("天津市");
        address.setDistrict("北辰区");
        address.setDetail("河北工业大学");
        address.setIsDefault(true);

        // 两家店各一件商品
        CartItemDTO itemA = cartItem("I135A", "S135A", "P135A", "店铺A商品", 5000L, 1);
        CartItemDTO itemB = cartItem("I135B", "S135B", "P135B", "店铺B商品", 7000L, 2);
        CartDTO cart = new CartDTO();
        cart.setCartId("C135");
        cart.setItems(List.of(itemA, itemB));

        when(cartClient.getCart(anyString(), anyString())).thenReturn(Result.success(cart));
        when(cartClient.deleteItem(anyString(), anyString(), anyString())).thenReturn(Result.success());
        when(userClient.addresses(anyString(), anyString())).thenReturn(Result.success(List.of(address)));
        when(userClient.shopBySeller(SELLER_A)).thenReturn(Result.success(shop(SELLER_A, "店铺A")));
        when(userClient.shopBySeller(SELLER_B)).thenReturn(Result.success(shop(SELLER_B, "店铺B")));
        when(productClient.getProduct("P135A")).thenReturn(Result.success(product("P135A", SELLER_A, "S135A", 5000L)));
        when(productClient.getProduct("P135B")).thenReturn(Result.success(product("P135B", SELLER_B, "S135B", 7000L)));
        when(productClient.reserve(anyString(), any(ReserveStockRequestDTO.class)))
                .thenAnswer(invocation -> {
                    ReserveStockRequestDTO request = invocation.getArgument(1);
                    return Result.success(new ReserveStockResponseDTO("R-" + request.getOrderId(),
                            request.getItems()));
                });
        when(productClient.release(anyString(), any(ProductClient.ReleaseRequest.class)))
                .thenReturn(Result.success(new ReleaseResponseDTO(true)));
    }

    @Test
    void previewGroupsByShopWithServerSideAmounts() {
        PreviewOrderRequest request = new PreviewOrderRequest();
        request.setCartItemIds(List.of("I135A", "I135B"));

        OrderPreviewResponse preview = orderService.preview(BUYER, request);

        assertThat(preview.getShopCount()).isEqualTo(2);
        assertThat(preview.getTotalAmount()).isEqualTo(5000L + 7000L * 2);
        assertThat(preview.getPayAmount()).isEqualTo(preview.getTotalAmount());
        assertThat(preview.getShops()).extracting(OrderPreviewResponse.ShopPreview::getSellerId)
                .containsExactly(SELLER_A, SELLER_B);
        assertThat(preview.getShops().get(0).getShopName()).isEqualTo("店铺A");
        assertThat(preview.getShops().get(0).getTotalAmount()).isEqualTo(5000L);
        assertThat(preview.getShops().get(1).getTotalAmount()).isEqualTo(14000L);
        // 预览不落库、不预占
        assertThat(count("SELECT COUNT(*) FROM `order`")).isZero();
        assertThat(count("SELECT COUNT(*) FROM main_order")).isZero();
        verify(productClient, times(0)).reserve(anyString(), any(ReserveStockRequestDTO.class));
    }

    @Test
    void crossShopCheckoutCreatesOneMainAndTwoSubOrders() {
        CreateOrderResponse response = orderService.create(BUYER, "idem-135-cross-01", request("I135A", "I135B"));

        assertThat(response.getMainOrderId()).isNotBlank();
        assertThat(response.getMainOrderNo()).isNotBlank();
        assertThat(response.getSubOrders()).hasSize(2);
        assertThat(response.getPayAmount()).isEqualTo(19000L);
        assertThat(response.getSubOrders()).extracting(CreateOrderResponse.SubOrderBrief::getSellerId)
                .containsExactly(SELLER_A, SELLER_B);

        assertThat(count("SELECT COUNT(*) FROM main_order WHERE is_deleted = 0")).isEqualTo(1);
        assertThat(count("SELECT COUNT(*) FROM `order` WHERE is_deleted = 0")).isEqualTo(2);
        MainOrder mainOrder = mainOrderMapper.selectById(response.getMainOrderId());
        assertThat(mainOrder.getStatus()).isEqualTo(OrderStatus.PENDING_PAYMENT);
        assertThat(mainOrder.getPayAmount()).isEqualTo(19000L);
        for (CreateOrderResponse.SubOrderBrief sub : response.getSubOrders()) {
            Order order = orderMapper.selectById(sub.getOrderId());
            assertThat(order.getMainOrderId()).isEqualTo(response.getMainOrderId());
            assertThat(order.getStatus()).isEqualTo(OrderStatus.PENDING_PAYMENT);
        }
        // 库存预占按子订单逐店进行
        verify(productClient, times(2)).reserve(anyString(), any(ReserveStockRequestDTO.class));
    }

    @Test
    void mainOrderBecomesPaidOnlyAfterAllSubOrdersPaid() {
        CreateOrderResponse response = orderService.create(BUYER, "idem-135-paid-01", request("I135A", "I135B"));
        String subA = response.getSubOrders().get(0).getOrderId();
        String subB = response.getSubOrders().get(1).getOrderId();

        orderService.markPaid(subA);
        assertThat(mainOrderMapper.selectById(response.getMainOrderId()).getStatus())
                .isEqualTo(OrderStatus.PENDING_PAYMENT);

        orderService.markPaid(subB);
        MainOrder mainOrder = mainOrderMapper.selectById(response.getMainOrderId());
        assertThat(mainOrder.getStatus()).isEqualTo(OrderStatus.PAID);
        assertThat(mainOrder.getPaidAt()).isNotNull();

        // 重复回调幂等：主订单保持 PAID
        orderService.markPaid(subA);
        assertThat(mainOrderMapper.selectById(response.getMainOrderId()).getStatus())
                .isEqualTo(OrderStatus.PAID);
    }

    @Test
    void subOrdersShipAndRefundIndependently() {
        CreateOrderResponse response = orderService.create(BUYER, "idem-135-ship-01", request("I135A", "I135B"));
        String subA = response.getSubOrders().get(0).getOrderId();
        String subB = response.getSubOrders().get(1).getOrderId();
        orderService.markPaid(subA);
        orderService.markPaid(subB);

        ShipOrderRequest shipA = new ShipOrderRequest();
        shipA.setTrackingNo("SF135A");
        orderService.ship(subA, SELLER_A, "SELLER", shipA);
        assertThat(orderMapper.selectById(subA).getStatus()).isEqualTo(OrderStatus.SHIPPED);
        assertThat(orderMapper.selectById(subB).getStatus()).isEqualTo(OrderStatus.PAID);

        // 子订单 A 退款不影响子订单 B 与主订单
        orderService.markRefunded(subA);
        assertThat(orderMapper.selectById(subA).getStatus()).isEqualTo(OrderStatus.REFUNDED);
        assertThat(orderMapper.selectById(subB).getStatus()).isEqualTo(OrderStatus.PAID);
        assertThat(mainOrderMapper.selectById(response.getMainOrderId()).getStatus())
                .isEqualTo(OrderStatus.PAID);

        ShipOrderRequest shipB = new ShipOrderRequest();
        shipB.setTrackingNo("SF135B");
        orderService.ship(subB, SELLER_B, "SELLER", shipB);
        orderService.confirm(subB, BUYER);
        assertThat(orderMapper.selectById(subB).getStatus()).isEqualTo(OrderStatus.COMPLETED);
    }

    @Test
    void cancelMainCancelsAllUnpaidSubOrdersAndReleasesInventory() {
        CreateOrderResponse response = orderService.create(BUYER, "idem-135-cancel-01", request("I135A", "I135B"));

        MainOrderDetailResponse detail = orderService.cancelMain(response.getMainOrderId(), BUYER);

        assertThat(detail.getStatus()).isEqualTo(OrderStatus.CANCELLED);
        assertThat(mainOrderMapper.selectById(response.getMainOrderId()).getStatus())
                .isEqualTo(OrderStatus.CANCELLED);
        for (CreateOrderResponse.SubOrderBrief sub : response.getSubOrders()) {
            Order order = orderMapper.selectById(sub.getOrderId());
            assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELLED);
            assertThat(order.getInventoryReleased()).isEqualTo(1);
        }
        verify(productClient, times(2)).release(anyString(), any(ProductClient.ReleaseRequest.class));

        // 重复取消幂等，不重复释放
        orderService.cancelMain(response.getMainOrderId(), BUYER);
        verify(productClient, times(2)).release(anyString(), any(ProductClient.ReleaseRequest.class));
    }

    @Test
    void cancelMainRejectsNonBuyerAndPaidMain() {
        CreateOrderResponse response = orderService.create(BUYER, "idem-135-cancel-02", request("I135A", "I135B"));
        assertThatThrownBy(() -> orderService.cancelMain(response.getMainOrderId(), "U-OTHER"))
                .isInstanceOf(BizException.class)
                .extracting(e -> ((BizException) e).getCode())
                .isEqualTo(ErrorCode.FORBIDDEN);

        for (CreateOrderResponse.SubOrderBrief sub : response.getSubOrders()) {
            orderService.markPaid(sub.getOrderId());
        }
        assertThatThrownBy(() -> orderService.cancelMain(response.getMainOrderId(), BUYER))
                .isInstanceOf(BizException.class)
                .extracting(e -> ((BizException) e).getCode())
                .isEqualTo(ErrorCode.ORDER_STATE_INVALID);
    }

    @Test
    void singleShopCheckoutCreatesNoMainOrder() {
        CreateOrderResponse response = orderService.create(BUYER, "idem-135-single-01", request("I135A"));

        assertThat(response.getMainOrderId()).isNull();
        assertThat(response.getMainOrderNo()).isNull();
        assertThat(response.getSubOrders()).isNull();
        assertThat(response.getPayAmount()).isEqualTo(5000L);
        assertThat(count("SELECT COUNT(*) FROM main_order")).isZero();
        assertThat(count("SELECT COUNT(*) FROM `order` WHERE is_deleted = 0")).isEqualTo(1);
        Order order = orderMapper.selectById(response.getOrderId());
        assertThat(order.getMainOrderId()).isNull();
        verify(productClient, times(1)).reserve(anyString(), any(ReserveStockRequestDTO.class));
    }

    @Test
    void legacySingleShopOrderDetailAndListStillWork() {
        // 历史单店订单：main_order_id 为 null
        jdbcTemplate.update("""
                INSERT INTO `order`(order_id, order_no, buyer_id, seller_id, main_order_id, status,
                                    total_amount, pay_amount, receiver, receiver_phone, address_full,
                                    expire_at, created_at, updated_at, is_deleted, inventory_released)
                VALUES ('O135-LEGACY', 'NO135-LEGACY', ?, ?, NULL, 'PAID',
                        9900, 9900, '张三', '13800138000', '天津市',
                        ?, ?, ?, 0, 0)
                """, BUYER, SELLER_A, LocalDateTime.now().plusMinutes(30),
                LocalDateTime.now(), LocalDateTime.now());

        assertThat(orderService.detail("O135-LEGACY", BUYER, "BUYER").getMainOrderId()).isNull();
        assertThat(orderService.list(BUYER, "buyer", null, 1, 20).list())
                .anySatisfy(item -> {
                    assertThat(item.getOrderId()).isEqualTo("O135-LEGACY");
                    assertThat(item.getMainOrderId()).isNull();
                });
    }

    @Test
    void repeatedSubmitWithSameKeyReturnsSameMainOrder() {
        CreateOrderResponse first = orderService.create(BUYER, "idem-135-repeat-01", request("I135A", "I135B"));
        CreateOrderResponse second = orderService.create(BUYER, "idem-135-repeat-01", request("I135A", "I135B"));

        assertThat(second.getMainOrderId()).isEqualTo(first.getMainOrderId());
        assertThat(second.getMainOrderNo()).isEqualTo(first.getMainOrderNo());
        assertThat(second.getPayAmount()).isEqualTo(first.getPayAmount());
        assertThat(second.getSubOrders()).extracting(CreateOrderResponse.SubOrderBrief::getOrderId)
                .containsExactlyElementsOf(first.getSubOrders().stream()
                        .map(CreateOrderResponse.SubOrderBrief::getOrderId).toList());
        assertThat(count("SELECT COUNT(*) FROM main_order WHERE is_deleted = 0")).isEqualTo(1);
        assertThat(count("SELECT COUNT(*) FROM `order` WHERE is_deleted = 0")).isEqualTo(2);
        verify(productClient, times(2)).reserve(anyString(), any(ReserveStockRequestDTO.class));
    }

    @Test
    void concurrentDuplicateKeyCreatesOnlyOneMainOrder() throws Exception {
        int attempts = 30;
        ExecutorService pool = Executors.newFixedThreadPool(15);
        CountDownLatch ready = new CountDownLatch(15);
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<CreateOrderResponse>> futures = java.util.stream.IntStream.range(0, attempts)
                    .mapToObj(index -> pool.submit(() -> {
                        ready.countDown();
                        assertThat(start.await(30, TimeUnit.SECONDS)).isTrue();
                        return orderService.create(BUYER, "idem-135-concurrent-01", request("I135A", "I135B"));
                    })).toList();
            assertThat(ready.await(30, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            List<CreateOrderResponse> responses = futures.stream().map(future -> {
                try {
                    return future.get(60, TimeUnit.SECONDS);
                } catch (Exception e) {
                    throw new IllegalStateException(e);
                }
            }).toList();
            Set<String> mainIds = responses.stream()
                    .map(CreateOrderResponse::getMainOrderId).collect(Collectors.toSet());
            assertThat(mainIds).hasSize(1);
            assertThat(responses).allSatisfy(response ->
                    assertThat(response.getPayAmount()).isEqualTo(19000L));
        } finally {
            pool.shutdownNow();
        }

        assertThat(count("SELECT COUNT(*) FROM main_order WHERE is_deleted = 0")).isEqualTo(1);
        assertThat(count("SELECT COUNT(*) FROM `order` WHERE is_deleted = 0")).isEqualTo(2);
        // 每店至多预占一次（并发重复提交不重复扣库存）
        verify(productClient, times(2)).reserve(anyString(), any(ReserveStockRequestDTO.class));
    }

    @Test
    void mainOrderTimeoutCancelsAllSubOrders() {
        CreateOrderResponse response = orderService.create(BUYER, "idem-135-timeout-01", request("I135A", "I135B"));
        jdbcTemplate.update("UPDATE main_order SET expire_at = ? WHERE main_order_id = ?",
                LocalDateTime.now().minusMinutes(1), response.getMainOrderId());

        assertThat(mainOrderMapper.selectTimeoutCandidates(LocalDateTime.now(), 10))
                .contains(response.getMainOrderId());
        assertThat(orderService.cancelMainTimeout(response.getMainOrderId())).isTrue();
        assertThat(mainOrderMapper.selectById(response.getMainOrderId()).getStatus())
                .isEqualTo(OrderStatus.CANCELLED);
        for (CreateOrderResponse.SubOrderBrief sub : response.getSubOrders()) {
            assertThat(orderMapper.selectById(sub.getOrderId()).getStatus())
                    .isEqualTo(OrderStatus.CANCELLED);
        }
        verify(productClient, times(2)).release(anyString(), any(ProductClient.ReleaseRequest.class));
        // 重复扫描幂等
        assertThat(orderService.cancelMainTimeout(response.getMainOrderId())).isFalse();
    }

    @Test
    void reserveFailureRollsBackWholeMainOrder() {
        when(productClient.reserve(anyString(), any(ReserveStockRequestDTO.class)))
                .thenAnswer(invocation -> {
                    ReserveStockRequestDTO request = invocation.getArgument(1);
                    if (request.getItems().stream().anyMatch(item -> "S135B".equals(item.getSkuId()))) {
                        throw new BizException(ErrorCode.STOCK_INSUFFICIENT, "库存不足: 店铺B商品");
                    }
                    return Result.success(new ReserveStockResponseDTO("R-" + request.getOrderId(),
                            request.getItems()));
                });

        assertThatThrownBy(() -> orderService.create(BUYER, "idem-135-fail-01", request("I135A", "I135B")))
                .isInstanceOf(BizException.class)
                .extracting(e -> ((BizException) e).getCode())
                .isEqualTo(ErrorCode.STOCK_INSUFFICIENT);

        assertThat(count("SELECT COUNT(*) FROM `order` WHERE is_deleted = 0")).isZero();
        assertThat(count("SELECT COUNT(*) FROM main_order WHERE is_deleted = 0")).isZero();
        // 已预占成功的店铺A被释放
        verify(productClient, atLeastOnce()).release(anyString(), any(ProductClient.ReleaseRequest.class));
        // 幂等键已退回，可重试
        assertThat(jdbcTemplate.queryForObject(
                "SELECT status FROM order_idempotency WHERE idem_key = 'idem-135-fail-01'", String.class))
                .isEqualTo("FAILED");
    }

    private static CreateOrderRequest request(String... cartItemIds) {
        CreateOrderRequest request = new CreateOrderRequest();
        request.setAddressId("A135");
        request.setCartItemIds(List.of(cartItemIds));
        return request;
    }

    private static CartItemDTO cartItem(String cartItemId, String skuId, String productId,
                                        String title, long price, int quantity) {
        CartItemDTO item = new CartItemDTO();
        item.setCartItemId(cartItemId);
        item.setSkuId(skuId);
        item.setProductId(productId);
        item.setTitle(title);
        item.setSpecName("默认规格");
        item.setPrice(price);
        item.setQuantity(quantity);
        item.setSelected(true);
        item.setStockAvailable(100);
        item.setInvalid(false);
        return item;
    }

    private static ProductDetailDTO product(String productId, String sellerId, String skuId, long price) {
        ProductDetailDTO product = new ProductDetailDTO();
        product.setProductId(productId);
        product.setSellerId(sellerId);
        product.setTitle("商品-" + productId);
        product.setStatus("ON_SALE");
        product.setPrice(price);
        SkuDTO sku = new SkuDTO();
        sku.setSkuId(skuId);
        sku.setSpecName("默认规格");
        sku.setPrice(price);
        sku.setStockAvailable(100);
        product.setSkus(List.of(sku));
        return product;
    }

    private static ShopBriefDTO shop(String sellerId, String name) {
        ShopBriefDTO shop = new ShopBriefDTO();
        shop.setShopId("SHOP-" + sellerId);
        shop.setSellerId(sellerId);
        shop.setName(name);
        shop.setStatus("ACTIVE");
        return shop;
    }

    private int count(String sql) {
        Integer value = jdbcTemplate.queryForObject(sql, Integer.class);
        return value == null ? 0 : value;
    }
}