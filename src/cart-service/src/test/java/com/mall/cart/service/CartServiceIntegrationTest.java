package com.mall.cart.service;

import com.mall.cart.client.ProductClient;
import com.mall.cart.client.dto.ProductDetailDTO;
import com.mall.cart.client.dto.ProductStatus;
import com.mall.cart.client.dto.SkuDTO;
import com.mall.cart.common.BizException;
import com.mall.cart.common.ErrorCode;
import com.mall.cart.common.Result;
import com.mall.cart.dto.request.AddCartItemRequest;
import com.mall.cart.dto.response.CartItemResponse;
import com.mall.cart.dto.response.CartResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@SpringBootTest
class CartServiceIntegrationTest {

    private static final String USER_ID = "U10001";
    private static final String OTHER_USER_ID = "U10002";
    private static final String PRODUCT_ID = "P30001";
    private static final String SKU_ID = "S40001";
    private static final String SKU_ID_2 = "S40002";

    @Autowired
    private CartService cartService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @MockBean
    private ProductClient productClient;

    @BeforeEach
    void cleanDatabase() {
        jdbcTemplate.update("DELETE FROM cart_item");
        jdbcTemplate.update("DELETE FROM cart");
    }

    @Test
    void addsNewItemAndAccumulatesSameSku() {
        stubProduct(product("ON_SALE", 5900L, 12));

        cartService.addCartItem(USER_ID, addRequest(SKU_ID, 2));
        cartService.addCartItem(USER_ID, addRequest(SKU_ID, 3));

        CartResponse cart = cartService.getCart(USER_ID);
        assertEquals(1, cart.getItems().size());
        CartItemResponse item = cart.getItems().get(0);
        assertEquals(SKU_ID, item.getSkuId());
        assertEquals(PRODUCT_ID, item.getProductId());
        assertEquals(5, item.getQuantity());
        assertTrue(item.getSelected());
        assertFalse(item.getInvalid());
        assertEquals(5900L, item.getPrice());
        assertEquals(12, item.getStockAvailable());
        assertEquals("罗技 G102 鼠标", item.getTitle());
        assertEquals(29500L, cart.getSelectedTotal());
        assertEquals(1, cart.getSelectedCount());
        assertEquals(1, count("cart"));
        assertEquals(1, count("cart_item"));
    }

    @Test
    void rejectsAccumulatedQuantityAboveStock() {
        stubProduct(product("ON_SALE", 5900L, 3));
        cartService.addCartItem(USER_ID, addRequest(SKU_ID, 2));

        BizException exception = assertThrows(BizException.class,
                () -> cartService.addCartItem(USER_ID, addRequest(SKU_ID, 2)));

        assertEquals(ErrorCode.QUANTITY_EXCEEDS_STOCK, exception.getCode());
        assertEquals(2, cartService.getCart(USER_ID).getItems().get(0).getQuantity());
    }

    @Test
    void rejectsSingleAddAboveStock() {
        stubProduct(product("ON_SALE", 5900L, 1));

        BizException exception = assertThrows(BizException.class,
                () -> cartService.addCartItem(USER_ID, addRequest(SKU_ID, 2)));

        assertEquals(ErrorCode.QUANTITY_EXCEEDS_STOCK, exception.getCode());
        assertEquals(0, count("cart"));
        assertEquals(0, count("cart_item"));
    }

    @Test
    void rejectsOffShelfProduct() {
        stubProduct(product("OFF_SHELF", 5900L, 10));

        BizException exception = assertThrows(BizException.class,
                () -> cartService.addCartItem(USER_ID, addRequest(SKU_ID, 1)));

        assertEquals(ErrorCode.PRODUCT_OFF_SHELF, exception.getCode());
        assertEquals(0, count("cart_item"));
    }

    @Test
    void rejectsMissingProductId() {
        AddCartItemRequest request = addRequest(SKU_ID, 1);
        request.setProductId(null);

        BizException exception = assertThrows(BizException.class,
                () -> cartService.addCartItem(USER_ID, request));

        assertEquals(ErrorCode.PARAM_INVALID, exception.getCode());
        verifyNoInteractions(productClient);
    }

    @Test
    void updatesQuantityAndSelection() {
        stubProduct(product("ON_SALE", 5900L, 12));
        cartService.addCartItem(USER_ID, addRequest(SKU_ID, 2));
        String itemId = cartService.getCart(USER_ID).getItems().get(0).getCartItemId();

        cartService.updateQuantity(USER_ID, itemId, 4);
        CartResponse cart = cartService.getCart(USER_ID);
        assertEquals(4, cart.getItems().get(0).getQuantity());
        assertEquals(23600L, cart.getSelectedTotal());

        cartService.updateSelected(USER_ID, itemId, false);
        cart = cartService.getCart(USER_ID);
        assertFalse(cart.getItems().get(0).getSelected());
        assertEquals(0L, cart.getSelectedTotal());
        assertEquals(0, cart.getSelectedCount());

        cartService.updateSelected(USER_ID, itemId, true);
        cart = cartService.getCart(USER_ID);
        assertTrue(cart.getItems().get(0).getSelected());
        assertEquals(23600L, cart.getSelectedTotal());
        assertEquals(1, cart.getSelectedCount());

        BizException aboveStock = assertThrows(BizException.class,
                () -> cartService.updateQuantity(USER_ID, itemId, 13));
        assertEquals(ErrorCode.QUANTITY_EXCEEDS_STOCK, aboveStock.getCode());

        BizException invalidQuantity = assertThrows(BizException.class,
                () -> cartService.updateQuantity(USER_ID, itemId, 0));
        assertEquals(ErrorCode.PARAM_INVALID, invalidQuantity.getCode());
    }

    @Test
    void marksItemInvalidWhenStockDrops() {
        stubProduct(product("ON_SALE", 5900L, 12));
        cartService.addCartItem(USER_ID, addRequest(SKU_ID, 3));

        stubProduct(product("ON_SALE", 5900L, 2));
        CartResponse cart = cartService.getCart(USER_ID);

        CartItemResponse item = cart.getItems().get(0);
        assertTrue(item.getInvalid());
        assertTrue(item.getInvalidReason().contains("库存不足"));
        assertEquals(2, item.getStockAvailable());
        assertEquals(0L, cart.getSelectedTotal());
        assertEquals(0, cart.getSelectedCount());
    }

    @Test
    void marksItemInvalidWhenProductOffShelfAfterAdding() {
        stubProduct(product("ON_SALE", 5900L, 12));
        cartService.addCartItem(USER_ID, addRequest(SKU_ID, 1));

        when(productClient.getProduct(eq(PRODUCT_ID), any()))
                .thenReturn(Result.fail(ErrorCode.PRODUCT_OFF_SHELF, "商品已下架或尚未审核通过"));

        CartResponse cart = cartService.getCart(USER_ID);
        CartItemResponse item = cart.getItems().get(0);
        assertTrue(item.getInvalid());
        assertEquals("商品已下架或尚未审核通过", item.getInvalidReason());
        assertEquals(0L, cart.getSelectedTotal());
    }

    @Test
    void keepsItemVisibleWhenProductServiceUnavailable() {
        stubProduct(product("ON_SALE", 5900L, 12));
        cartService.addCartItem(USER_ID, addRequest(SKU_ID, 1));

        when(productClient.getProduct(eq(PRODUCT_ID), any()))
                .thenThrow(new RuntimeException("connect timed out"));

        CartResponse cart = cartService.getCart(USER_ID);
        assertEquals(1, cart.getItems().size());
        CartItemResponse item = cart.getItems().get(0);
        assertTrue(item.getInvalid());
        assertTrue(item.getInvalidReason().contains("无法获取"));
        assertEquals(0L, item.getPrice());
    }

    @Test
    void deletesItemAndReAddResurrectsIt() {
        stubProduct(product("ON_SALE", 5900L, 12));
        cartService.addCartItem(USER_ID, addRequest(SKU_ID, 1));
        String itemId = cartService.getCart(USER_ID).getItems().get(0).getCartItemId();

        cartService.removeItem(USER_ID, itemId);
        assertEquals(0, cartService.getCart(USER_ID).getItems().size());
        assertEquals(0, countActiveItems());

        cartService.addCartItem(USER_ID, addRequest(SKU_ID, 3));
        CartResponse cart = cartService.getCart(USER_ID);
        assertEquals(1, cart.getItems().size());
        assertEquals(itemId, cart.getItems().get(0).getCartItemId());
        assertEquals(3, cart.getItems().get(0).getQuantity());
        assertEquals(1, count("cart_item"));

        BizException missing = assertThrows(BizException.class,
                () -> cartService.removeItem(USER_ID, "I-not-exists"));
        assertEquals(ErrorCode.CART_ITEM_NOT_FOUND, missing.getCode());
    }

    @Test
    void clearsCart() {
        stubProduct(product("ON_SALE", 5900L, 12, SKU_ID, SKU_ID_2));
        cartService.addCartItem(USER_ID, addRequest(SKU_ID, 1));
        cartService.addCartItem(USER_ID, addRequest(SKU_ID_2, 2));
        assertEquals(2, cartService.getCart(USER_ID).getItems().size());

        cartService.clearCart(USER_ID);

        assertEquals(0, cartService.getCart(USER_ID).getItems().size());
        assertEquals(0, countActiveItems());
        assertEquals(2, count("cart_item"));
    }

    @Test
    void blocksOperationsOnOtherUsersItem() {
        stubProduct(product("ON_SALE", 5900L, 12));
        cartService.addCartItem(USER_ID, addRequest(SKU_ID, 2));
        String itemId = cartService.getCart(USER_ID).getItems().get(0).getCartItemId();

        BizException select = assertThrows(BizException.class,
                () -> cartService.updateSelected(OTHER_USER_ID, itemId, false));
        BizException update = assertThrows(BizException.class,
                () -> cartService.updateQuantity(OTHER_USER_ID, itemId, 1));
        BizException remove = assertThrows(BizException.class,
                () -> cartService.removeItem(OTHER_USER_ID, itemId));

        assertEquals(ErrorCode.CART_ITEM_NOT_FOUND, select.getCode());
        assertEquals(ErrorCode.CART_ITEM_NOT_FOUND, update.getCode());
        assertEquals(ErrorCode.CART_ITEM_NOT_FOUND, remove.getCode());
        assertEquals(1, countActiveItems());
    }

    @Test
    void returnsEmptyCartForNewUser() {
        CartResponse cart = cartService.getCart(USER_ID);

        assertNull(cart.getCartId());
        assertTrue(cart.getItems().isEmpty());
        assertEquals(0L, cart.getSelectedTotal());
        assertEquals(0, cart.getSelectedCount());
    }

    @Test
    void requiresUserId() {
        BizException read = assertThrows(BizException.class, () -> cartService.getCart(" "));
        BizException write = assertThrows(BizException.class,
                () -> cartService.addCartItem(null, addRequest(SKU_ID, 1)));

        assertEquals(ErrorCode.UNAUTHORIZED, read.getCode());
        assertEquals(ErrorCode.UNAUTHORIZED, write.getCode());
    }

    private void stubProduct(ProductDetailDTO product) {
        when(productClient.getProduct(eq(product.getProductId()), any()))
                .thenReturn(Result.success(product));
    }

    private AddCartItemRequest addRequest(String skuId, int quantity) {
        AddCartItemRequest request = new AddCartItemRequest();
        request.setSkuId(skuId);
        request.setProductId(PRODUCT_ID);
        request.setQuantity(quantity);
        return request;
    }

    private ProductDetailDTO product(String status, long price, int stock, String... skuIds) {
        ProductDetailDTO product = new ProductDetailDTO();
        product.setProductId(PRODUCT_ID);
        product.setSellerId("U20001");
        product.setTitle("罗技 G102 鼠标");
        product.setMainImage("/images/p30001.jpg");
        product.setStatus(ProductStatus.valueOf(status));
        product.setPrice(price);
        if (skuIds.length == 0) {
            skuIds = new String[]{SKU_ID};
        }
        List<SkuDTO> skus = new ArrayList<>();
        for (String skuId : skuIds) {
            SkuDTO sku = new SkuDTO();
            sku.setSkuId(skuId);
            sku.setSpecName("黑色");
            sku.setPrice(price);
            sku.setStockAvailable(stock);
            sku.setStockLocked(0);
            skus.add(sku);
        }
        product.setSkus(skus);
        return product;
    }

    private int count(String table) {
        Integer count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM " + table, Integer.class);
        return count == null ? 0 : count;
    }

    private int countActiveItems() {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM cart_item WHERE is_deleted = 0", Integer.class);
        return count == null ? 0 : count;
    }
}
