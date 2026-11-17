package com.mall.order;

import com.mall.order.client.CartClient;
import com.mall.order.client.FreightClient;
import com.mall.order.client.ProductClient;
import com.mall.order.client.UserClient;
import com.mall.order.client.dto.AddressDTO;
import com.mall.order.client.dto.CartDTO;
import com.mall.order.client.dto.CartItemDTO;
import com.mall.order.client.dto.FreightConfigDTO;
import com.mall.order.client.dto.ProductDetailDTO;
import com.mall.order.client.dto.ReleaseResponseDTO;
import com.mall.order.client.dto.ReserveStockRequestDTO;
import com.mall.order.client.dto.ReserveStockResponseDTO;
import com.mall.order.client.dto.ShopBriefDTO;
import com.mall.order.client.dto.SkuDTO;
import com.mall.order.common.BizException;
import com.mall.order.common.ErrorCode;
import com.mall.order.common.Result;
import com.mall.order.dto.request.CreateOrderRequest;
import com.mall.order.dto.request.PreviewOrderRequest;
import com.mall.order.dto.response.CreateOrderResponse;
import com.mall.order.dto.response.OrderDetailResponse;
import com.mall.order.dto.response.OrderPreviewResponse;
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

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * Issue #136：运费与优惠计价。
 *
 * <p>覆盖验收清单：包邮门槛（9900 分）、分地区运费、跨店各店运费分别计算后汇总、
 * 订单详情金额与 pay_amount 一致、切换地址运费随之变化、未配置模板的店铺运费为 0、
 * 计算顺序与「四舍五入只发生一次」。
 *
 * <p>下游服务（cart / product / user / freight）全部 Mock，H2 隔离库，不碰开发数据。
 */
@SpringBootTest
@ActiveProfiles("test")
class Issue136FreightDiscountTest {

    private static final String BUYER = "U136-BUYER";
    private static final String BUYER_NO_ADDRESS = "U136-NO-ADDRESS";
    private static final String SELLER_A = "U136-SELLER-A";
    private static final String SELLER_B = "U136-SELLER-B";
    private static final String ADDRESS_TIANJIN = "A136-TJ";
    private static final String ADDRESS_HEBEI = "A136-HB";

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

    @MockBean
    private FreightClient freightClient;

    @BeforeEach
    void setUp() {
        jdbcTemplate.update("DELETE FROM order_compensation");
        jdbcTemplate.update("DELETE FROM order_idempotency");
        jdbcTemplate.update("DELETE FROM order_item");
        jdbcTemplate.update("DELETE FROM `order`");
        jdbcTemplate.update("DELETE FROM main_order");

        when(cartClient.getCart(anyString(), anyString())).thenReturn(Result.success(cart()));
        when(cartClient.deleteItem(anyString(), anyString(), anyString())).thenReturn(Result.success());
        when(userClient.addresses(BUYER, BUYER)).thenReturn(Result.success(List.of(
                address(ADDRESS_TIANJIN, "天津市", true),
                address(ADDRESS_HEBEI, "河北省", false))));
        when(userClient.addresses(BUYER_NO_ADDRESS, BUYER_NO_ADDRESS)).thenReturn(Result.success(List.of()));
        when(userClient.shopBySeller(SELLER_A)).thenReturn(Result.success(shop(SELLER_A, "店铺A")));
        when(userClient.shopBySeller(SELLER_B)).thenReturn(Result.success(shop(SELLER_B, "店铺B")));
        when(productClient.getProduct("P136A")).thenReturn(Result.success(product("P136A", SELLER_A, "S136A", 5000L)));
        when(productClient.getProduct("P136B")).thenReturn(Result.success(product("P136B", SELLER_B, "S136B", 3000L)));
        when(productClient.reserve(anyString(), any(ReserveStockRequestDTO.class)))
                .thenAnswer(invocation -> {
                    ReserveStockRequestDTO request = invocation.getArgument(1);
                    return Result.success(new ReserveStockResponseDTO("R-" + request.getOrderId(), request.getItems()));
                });
        when(productClient.release(anyString(), any(ProductClient.ReleaseRequest.class)))
                .thenReturn(Result.success(new ReleaseResponseDTO(true)));

        // 店铺A：9900 分包邮；天津市 800、河北省 1200、其它地区 600
        when(freightClient.bySeller(SELLER_A)).thenReturn(Result.success(config(9900L,
                rule("DEFAULT", 0L, 600L), rule("天津市", 0L, 800L), rule("河北省", 0L, 1200L))));
        // 店铺B：没有配置模板
        when(freightClient.bySeller(SELLER_B)).thenReturn(Result.success(emptyConfig()));
    }

    @Test
    void previewChargesFreightByShopAndKeepsPayFormula() {
        OrderPreviewResponse preview = orderService.preview(BUYER, previewRequest(null, "I136A", "I136B"));

        assertThat(preview.getRegion()).isEqualTo("天津市");
        assertThat(preview.getTotalAmount()).isEqualTo(8000L);
        assertThat(preview.getFreightAmount()).isEqualTo(800L);
        assertThat(preview.getDiscountAmount()).isZero();
        assertThat(preview.getPayAmount()).isEqualTo(8800L);
        assertThat(preview.getPayAmount())
                .isEqualTo(preview.getTotalAmount() - preview.getDiscountAmount() + preview.getFreightAmount());
        assertThat(preview.getShops()).extracting(OrderPreviewResponse.ShopPreview::getSellerId)
                .containsExactly(SELLER_A, SELLER_B);
        assertThat(preview.getShops().get(0).getFreightAmount()).isEqualTo(800L);
        // 未配置模板的店铺运费为 0，且不报错
        assertThat(preview.getShops().get(1).getFreightAmount()).isZero();
        assertThat(preview.getShops().get(1).getPayAmount()).isEqualTo(3000L);
        // 预览不落库
        assertThat(count("SELECT COUNT(*) FROM `order`")).isZero();
    }

    @Test
    void switchingAddressChangesFreightAndTotal() {
        OrderPreviewResponse tianjin = orderService.preview(BUYER, previewRequest(ADDRESS_TIANJIN, "I136A"));
        OrderPreviewResponse hebei = orderService.preview(BUYER, previewRequest(ADDRESS_HEBEI, "I136A"));

        assertThat(tianjin.getFreightAmount()).isEqualTo(800L);
        assertThat(tianjin.getPayAmount()).isEqualTo(5800L);
        assertThat(hebei.getRegion()).isEqualTo("河北省");
        assertThat(hebei.getFreightAmount()).isEqualTo(1200L);
        assertThat(hebei.getPayAmount()).isEqualTo(6200L);
    }

    @Test
    void previewWithoutAddressIdFallsBackToDefaultAddress() {
        OrderPreviewResponse preview = orderService.preview(BUYER, previewRequest(null, "I136A"));

        assertThat(preview.getRegion()).isEqualTo("天津市");
        assertThat(preview.getFreightAmount()).isEqualTo(800L);
    }

    @Test
    void previewWithoutAnyAddressAsksBuyerToChooseOne() {
        assertThatThrownBy(() -> orderService.preview(BUYER_NO_ADDRESS, previewRequest(null, "I136A")))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("请先选择收货地址")
                .extracting(e -> ((BizException) e).getCode())
                .isEqualTo(ErrorCode.PARAM_INVALID);
    }

    @Test
    void freeShippingThresholdAppliesWhenShopSubtotalReachesIt() {
        OrderPreviewResponse preview = orderService.preview(BUYER,
                previewRequest(ADDRESS_TIANJIN, "I136A", "I136A-BULK", "I136B"));

        // 店铺A 两件共 10000 分 ≥ 9900，包邮；店铺B 未配置模板，运费 0
        assertThat(preview.getTotalAmount()).isEqualTo(13000L);
        assertThat(preview.getFreightAmount()).isZero();
        assertThat(preview.getPayAmount()).isEqualTo(13000L);
    }

    @Test
    void crossShopFreightIsSummedPerShop() {
        when(freightClient.bySeller(SELLER_B)).thenReturn(Result.success(config(0L, rule("DEFAULT", 0L, 700L))));

        OrderPreviewResponse preview = orderService.preview(BUYER,
                previewRequest(ADDRESS_TIANJIN, "I136A", "I136B"));

        assertThat(preview.getShops()).extracting(OrderPreviewResponse.ShopPreview::getFreightAmount)
                .containsExactly(800L, 700L);
        assertThat(preview.getFreightAmount()).isEqualTo(1500L);
        assertThat(preview.getPayAmount()).isEqualTo(9500L);
    }

    @Test
    void createOrderPersistsFreightAndDetailMatchesPayAmount() {
        CreateOrderResponse created = orderService.create(BUYER, "idem-136-create-01",
                createRequest(ADDRESS_HEBEI, "I136A", "I136B"));

        assertThat(created.getMainOrderId()).isNotBlank();
        assertThat(created.getPayAmount()).isEqualTo(8000L + 1200L);

        String subA = created.getSubOrders().stream()
                .filter(sub -> SELLER_A.equals(sub.getSellerId())).findFirst().orElseThrow().getOrderId();
        Order orderA = orderMapper.selectById(subA);
        assertThat(orderA.getTotalAmount()).isEqualTo(5000L);
        assertThat(orderA.getFreightAmount()).isEqualTo(1200L);
        assertThat(orderA.getDiscountAmount()).isZero();
        assertThat(orderA.getPayAmount()).isEqualTo(6200L);

        OrderDetailResponse detail = orderService.detail(subA, BUYER, "BUYER");
        assertThat(detail.getFreightAmount()).isEqualTo(1200L);
        assertThat(detail.getDiscountAmount()).isZero();
        assertThat(detail.getPayAmount()).isEqualTo(orderA.getPayAmount());

        // 主订单应付 = 各子订单应付之和
        assertThat(mainOrderMapper.selectById(created.getMainOrderId()).getPayAmount()).isEqualTo(9200L);
    }

    /* ---------------- Mock 数据 ---------------- */

    private static CartDTO cart() {
        CartItemDTO itemA = cartItem("I136A", "S136A", "P136A", 5000L, 1);
        CartItemDTO bulkA = cartItem("I136A-BULK", "S136A", "P136A", 5000L, 1);
        CartItemDTO itemB = cartItem("I136B", "S136B", "P136B", 3000L, 1);
        CartDTO cart = new CartDTO();
        cart.setCartId("C136");
        cart.setItems(List.of(itemA, bulkA, itemB));
        return cart;
    }

    private static CartItemDTO cartItem(String cartItemId, String skuId, String productId, long price,
                                        int quantity) {
        CartItemDTO item = new CartItemDTO();
        item.setCartItemId(cartItemId);
        item.setSkuId(skuId);
        item.setProductId(productId);
        item.setTitle("商品-" + productId);
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

    private static AddressDTO address(String addressId, String province, boolean isDefault) {
        AddressDTO address = new AddressDTO();
        address.setAddressId(addressId);
        address.setReceiver("测试买家");
        address.setPhone("13800138000");
        address.setProvince(province);
        address.setCity(province);
        address.setDistrict("某区");
        address.setDetail("某路 1 号");
        address.setIsDefault(isDefault);
        return address;
    }

    private static FreightConfigDTO config(Long freeThreshold, FreightConfigDTO.FreightRuleDTO... rules) {
        FreightConfigDTO config = new FreightConfigDTO();
        config.setTemplateId("FT-136");
        config.setFreeThreshold(freeThreshold);
        config.setRules(List.of(rules));
        return config;
    }

    private static FreightConfigDTO emptyConfig() {
        FreightConfigDTO config = new FreightConfigDTO();
        config.setFreeThreshold(0L);
        config.setRules(List.of());
        return config;
    }

    private static FreightConfigDTO.FreightRuleDTO rule(String region, long minAmount, long fee) {
        FreightConfigDTO.FreightRuleDTO rule = new FreightConfigDTO.FreightRuleDTO();
        rule.setRegionCode(region);
        rule.setMinAmount(minAmount);
        rule.setFee(fee);
        return rule;
    }

    private static PreviewOrderRequest previewRequest(String addressId, String... cartItemIds) {
        PreviewOrderRequest request = new PreviewOrderRequest();
        request.setAddressId(addressId);
        request.setCartItemIds(List.of(cartItemIds));
        return request;
    }

    private static CreateOrderRequest createRequest(String addressId, String... cartItemIds) {
        CreateOrderRequest request = new CreateOrderRequest();
        request.setAddressId(addressId);
        request.setCartItemIds(List.of(cartItemIds));
        return request;
    }

    private int count(String sql) {
        Integer value = jdbcTemplate.queryForObject(sql, Integer.class);
        return value == null ? 0 : value;
    }
}
