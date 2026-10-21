package com.mall.cart.controller;

import com.mall.cart.client.ProductClient;
import com.mall.cart.client.dto.ProductDetailDTO;
import com.mall.cart.client.dto.ProductStatus;
import com.mall.cart.client.dto.SkuDTO;
import com.mall.cart.common.Result;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class CartControllerTest {

    private static final String USER_ID = "U10001";
    private static final String PRODUCT_ID = "P30001";
    private static final String SKU_ID = "S40001";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @MockBean
    private ProductClient productClient;

    @BeforeEach
    void setUp() {
        jdbcTemplate.update("DELETE FROM cart_item");
        jdbcTemplate.update("DELETE FROM cart");
        when(productClient.getProduct(eq(PRODUCT_ID), any()))
                .thenReturn(Result.success(product()));
    }

    @Test
    void supportsGatewayPathAndInternalPath() throws Exception {
        mockMvc.perform(post("/api/cart/items")
                        .header("X-User-Id", USER_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"skuId\":\"" + SKU_ID + "\",\"productId\":\"" + PRODUCT_ID + "\",\"quantity\":2}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));

        mockMvc.perform(get("/cart").header("X-User-Id", USER_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.items.length()").value(1))
                .andExpect(jsonPath("$.data.items[0].skuId").value(SKU_ID))
                .andExpect(jsonPath("$.data.items[0].quantity").value(2))
                .andExpect(jsonPath("$.data.items[0].price").value(5900))
                .andExpect(jsonPath("$.data.selectedTotal").value(11800));
    }

    @Test
    void togglesAndRemovesItem() throws Exception {
        mockMvc.perform(post("/api/cart/items")
                        .header("X-User-Id", USER_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"skuId\":\"" + SKU_ID + "\",\"productId\":\"" + PRODUCT_ID + "\",\"quantity\":1}"))
                .andExpect(jsonPath("$.code").value(0));

        String cartItemId = jdbcTemplate.queryForObject(
                "SELECT cart_item_id FROM cart_item WHERE is_deleted = 0", String.class);

        mockMvc.perform(put("/api/cart/items/{id}/select", cartItemId)
                        .header("X-User-Id", USER_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"selected\":false}"))
                .andExpect(jsonPath("$.code").value(0));

        mockMvc.perform(get("/api/cart").header("X-User-Id", USER_ID))
                .andExpect(jsonPath("$.data.items[0].selected").value(false))
                .andExpect(jsonPath("$.data.selectedTotal").value(0));

        mockMvc.perform(delete("/api/cart/items/{id}", cartItemId)
                        .header("X-User-Id", USER_ID))
                .andExpect(jsonPath("$.code").value(0));

        mockMvc.perform(get("/api/cart").header("X-User-Id", USER_ID))
                .andExpect(jsonPath("$.data.items.length()").value(0));
    }

    @Test
    void rejectsMissingUserHeader() throws Exception {
        mockMvc.perform(get("/api/cart"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(10002));
    }

    @Test
    void rejectsInvalidQuantity() throws Exception {
        mockMvc.perform(post("/api/cart/items")
                        .header("X-User-Id", USER_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"skuId\":\"" + SKU_ID + "\",\"productId\":\"" + PRODUCT_ID + "\",\"quantity\":0}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(10001));
    }

    @Test
    void returnsCartItemNotFoundForUnknownItem() throws Exception {
        mockMvc.perform(delete("/api/cart/items/{id}", "I-not-exists")
                        .header("X-User-Id", USER_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(40001));
    }

    private ProductDetailDTO product() {
        ProductDetailDTO product = new ProductDetailDTO();
        product.setProductId(PRODUCT_ID);
        product.setSellerId("U20001");
        product.setTitle("罗技 G102 鼠标");
        product.setMainImage("/images/p30001.jpg");
        product.setStatus(ProductStatus.ON_SALE);
        product.setPrice(5900L);

        SkuDTO sku = new SkuDTO();
        sku.setSkuId(SKU_ID);
        sku.setSpecName("黑色");
        sku.setPrice(5900L);
        sku.setStockAvailable(12);
        sku.setStockLocked(0);
        product.setSkus(List.of(sku));
        return product;
    }
}
