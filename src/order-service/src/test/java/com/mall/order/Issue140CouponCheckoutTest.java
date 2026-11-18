package com.mall.order;

import com.mall.order.client.CartClient;
import com.mall.order.client.CouponClient;
import com.mall.order.client.FreightClient;
import com.mall.order.client.ProductClient;
import com.mall.order.client.UserClient;
import com.mall.order.client.dto.AddressDTO;
import com.mall.order.client.dto.CartDTO;
import com.mall.order.client.dto.CartItemDTO;
import com.mall.order.client.dto.FreightConfigDTO;
import com.mall.order.client.dto.OccupyCouponRequestDTO;
import com.mall.order.client.dto.ProductDetailDTO;
import com.mall.order.client.dto.ReleaseCouponRequestDTO;
import com.mall.order.client.dto.ReleaseResponseDTO;
import com.mall.order.client.dto.ReserveStockRequestDTO;
import com.mall.order.client.dto.ReserveStockResponseDTO;
import com.mall.order.client.dto.ShopBriefDTO;
import com.mall.order.client.dto.SkuDTO;
import com.mall.order.client.dto.UserCouponDTO;
import com.mall.order.common.BizException;
import com.mall.order.common.ErrorCode;
import com.mall.order.common.Result;
import com.mall.order.dto.request.CreateOrderRequest;
import com.mall.order.dto.request.PreviewOrderRequest;
import com.mall.order.dto.response.CreateOrderResponse;
import com.mall.order.dto.response.OrderDetailResponse;
import com.mall.order.dto.response.OrderPreviewResponse;
import com.mall.order.entity.Order;
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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Issue #140：下单用券全链路（校验 → 计价 → 占用 → 取消释放）。
 *
 * <p>覆盖验收：预览/下单共用券校验与计价；券不归属/不适用店铺被拒；
 * 占用失败与库存预占失败同路径清理（软删除 + 释放预占）；取消订单返还券。
 */
@SpringBootTest
@ActiveProfiles("test")
class Issue140CouponCheckoutTest {

    private static final String BUYER = "U140-BUYER";
    private static final String OTHER = "U140-OTHER";
    private static final String SELLER_A = "U140-SELLER-A";
    private static final String SELLER_B = "U140-SELLER-B";
    private static final String ADDRESS = "A140-1";
    private static final String USER_COUPON_ID = "UC-140-1";

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

    @MockBean
    private FreightClient freightClient;

    @MockBean
    private CouponClient couponClient;

    @MockBean
    private com.mall.order.client.PromotionClient promotionClient;

    @BeforeEach
    void setUp() {
        jdbcTemplate.update("DELETE FROM order_compensation");
        jdbcTemplate.update("DELETE FROM order_idempotency");
        jdbcTemplate.update("DELETE FROM order_item");
        jdbcTemplate.update("DELETE FROM `order`");
        jdbcTemplate.update("DELETE FROM main_order");

        when(cartClient.getCart(anyString(), anyString())).thenReturn(Result.success(cart()));
        when(cartClient.deleteItem(anyString(), anyString(), anyString())).thenReturn(Result.success());
        when(userClient.addresses(BUYER, BUYER)).thenReturn(Result.success(List.of(address())));
        when(userClient.shopBySeller(anyString())).thenReturn(Result.success(shop(SELLER_A, "店铺A")));
        when(productClient.getProduct("P140A")).thenReturn(Result.success(product("P140A", SELLER_A, "S140A", 5000L)));
        when(productClient.getProduct("P140B")).thenReturn(Result.success(product("P140B", SELLER_B, "S140B", 3000L)));
        when(productClient.reserve(anyString(), any(ReserveStockRequestDTO.class)))
                .thenAnswer(invocation -> {
                    ReserveStockRequestDTO request = invocation.getArgument(1);
                    return Result.success(new ReserveStockResponseDTO("R-" + request.getOrderId(), request.getItems()));
                });
        when(productClient.release(anyString(), any(ProductClient.ReleaseRequest.class)))
                .thenReturn(Result.success(new ReleaseResponseDTO(true)));
        when(freightClient.bySeller(anyString())).thenReturn(Result.success(emptyConfig()));
        when(promotionClient.active(any())).thenReturn(Result.success(List.of()));
        when(couponClient.detail(any(), anyString())).thenReturn(Result.success(coupon(SELLER_A, "FIXED", 500L, 1000L)));
        when(couponClient.occupy(any(), any(OccupyCouponRequestDTO.class)))
                .thenReturn(Result.success(coupon(SELLER_A, "FIXED", 500L, 1000L)));
        when(couponClient.release(any(), any(ReleaseCouponRequestDTO.class))).thenReturn(Result.success());
    }

    @Test
    void createWithCouponOccupiesAndDiscounts() {
        CreateOrderResponse created = orderService.create(BUYER, "idem-140-create-01",
                createRequest("I140A", USER_COUPON_ID));

        // 券挂到券所属店铺的子订单上
        verify(couponClient).occupy(any(), any(OccupyCouponRequestDTO.class));
        Order order = orderMapper.selectById(created.getOrderId());
        assertThat(order.getDiscountAmount()).isEqualTo(500L);
        assertThat(order.getPayAmount()).isEqualTo(4500L);
        assertThat(created.getPayAmount()).isEqualTo(4500L);
    }

    @Test
    void previewAppliesCouponDiscount() {
        OrderPreviewResponse preview = orderService.preview(BUYER, previewRequest("I140A", USER_COUPON_ID));

        assertThat(preview.getTotalAmount()).isEqualTo(5000L);
        assertThat(preview.getDiscountAmount()).isEqualTo(500L);
        assertThat(preview.getPayAmount()).isEqualTo(4500L);
        assertThat(count("SELECT COUNT(*) FROM `order`")).isZero();
    }

    @Test
    void couponOfAnotherBuyerIsRejected() {
        when(couponClient.detail(any(), anyString()))
                .thenReturn(Result.success(couponOf(OTHER, SELLER_A, "FIXED", 500L, 1000L)));

        assertThatThrownBy(() -> orderService.preview(BUYER, previewRequest("I140A", USER_COUPON_ID)))
                .isInstanceOf(BizException.class)
                .extracting(e -> ((BizException) e).getCode())
                .isEqualTo(ErrorCode.FORBIDDEN);
    }

    @Test
    void couponForShopNotInCartIsRejected() {
        when(couponClient.detail(any(), anyString()))
                .thenReturn(Result.success(coupon("U140-SELLER-X", "FIXED", 500L, 1000L)));

        assertThatThrownBy(() -> orderService.preview(BUYER, previewRequest("I140A", USER_COUPON_ID)))
                .isInstanceOf(BizException.class)
                .extracting(e -> ((BizException) e).getCode())
                .isEqualTo(ErrorCode.PARAM_INVALID);
    }

    @Test
    void occupyFailureRollsBackOrderLikeStockFailure() {
        when(couponClient.occupy(any(), any(OccupyCouponRequestDTO.class)))
                .thenReturn(Result.fail(ErrorCode.COUPON_ALREADY_USED, "优惠券已被使用，请刷新后重试"));

        assertThatThrownBy(() -> orderService.create(BUYER, "idem-140-create-02",
                createRequest("I140A", USER_COUPON_ID)))
                .isInstanceOf(BizException.class)
                .extracting(e -> ((BizException) e).getCode())
                .isEqualTo(ErrorCode.COUPON_ALREADY_USED);

        // 与库存预占失败同路径：订单软删除、预占释放
        assertThat(count("SELECT COUNT(*) FROM `order` WHERE is_deleted = 0")).isZero();
        verify(productClient).release(anyString(), any(ProductClient.ReleaseRequest.class));
    }

    @Test
    void cancelReleasesOccupiedCoupon() {
        CreateOrderResponse created = orderService.create(BUYER, "idem-140-create-03",
                createRequest("I140A", USER_COUPON_ID));

        orderService.cancel(created.getOrderId(), BUYER);

        verify(couponClient).release(any(), any(ReleaseCouponRequestDTO.class));
        OrderDetailResponse detail = orderService.detail(created.getOrderId(), BUYER, "BUYER");
        assertThat(detail.getStatus().name()).isEqualTo("CANCELLED");
    }

    @Test
    void createWithoutCouponNeverTouchesCouponService() {
        orderService.create(BUYER, "idem-140-create-04", createRequest("I140A", null));

        verify(couponClient, never()).detail(any(), anyString());
        verify(couponClient, never()).occupy(any(), any(OccupyCouponRequestDTO.class));
    }

    /* ---------------- Mock 数据 ---------------- */

    private static CartDTO cart() {
        CartItemDTO itemA = cartItem("I140A", "S140A", "P140A", 5000L);
        CartItemDTO itemB = cartItem("I140B", "S140B", "P140B", 3000L);
        CartDTO cart = new CartDTO();
        cart.setCartId("C140");
        cart.setItems(List.of(itemA, itemB));
        return cart;
    }

    private static CartItemDTO cartItem(String cartItemId, String skuId, String productId, long price) {
        CartItemDTO item = new CartItemDTO();
        item.setCartItemId(cartItemId);
        item.setSkuId(skuId);
        item.setProductId(productId);
        item.setTitle("商品-" + productId);
        item.setSpecName("默认规格");
        item.setPrice(price);
        item.setQuantity(1);
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

    private static AddressDTO address() {
        AddressDTO address = new AddressDTO();
        address.setAddressId(ADDRESS);
        address.setReceiver("测试买家");
        address.setPhone("13800138000");
        address.setProvince("天津市");
        address.setCity("天津市");
        address.setDistrict("某区");
        address.setDetail("某路 1 号");
        address.setIsDefault(true);
        return address;
    }

    private static FreightConfigDTO emptyConfig() {
        FreightConfigDTO config = new FreightConfigDTO();
        config.setTemplateId("FT-EMPTY");
        config.setFreeThreshold(0L);
        config.setRules(List.of());
        return config;
    }

    private static UserCouponDTO coupon(String shopId, String type, long value, long minAmount) {
        return couponOf(BUYER, shopId, type, value, minAmount);
    }

    private static UserCouponDTO couponOf(String userId, String shopId, String type,
                                          long value, long minAmount) {
        UserCouponDTO dto = new UserCouponDTO();
        dto.setUserCouponId(USER_COUPON_ID);
        dto.setCouponId("C-140-1");
        dto.setShopId(shopId);
        dto.setUserId(userId);
        dto.setName("测试券");
        dto.setType(type);
        dto.setValue(value);
        dto.setMinAmount(minAmount);
        dto.setStatus("UNUSED");
        dto.setStartAt(LocalDateTime.now().minusHours(1));
        dto.setEndAt(LocalDateTime.now().plusDays(7));
        return dto;
    }

    private static PreviewOrderRequest previewRequest(String cartItemId, String userCouponId) {
        PreviewOrderRequest request = new PreviewOrderRequest();
        request.setCartItemIds(List.of(cartItemId));
        request.setUserCouponId(userCouponId);
        return request;
    }

    private static CreateOrderRequest createRequest(String cartItemId, String userCouponId) {
        CreateOrderRequest request = new CreateOrderRequest();
        request.setAddressId(ADDRESS);
        request.setCartItemIds(List.of(cartItemId));
        request.setUserCouponId(userCouponId);
        return request;
    }

    private int count(String sql) {
        Integer value = jdbcTemplate.queryForObject(sql, Integer.class);
        return value == null ? 0 : value;
    }
}
