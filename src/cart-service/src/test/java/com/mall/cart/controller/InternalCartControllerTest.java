package com.mall.cart.controller;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class InternalCartControllerTest {

    private static final String USER_ID = "U10001";
    private static final String EMPTY_USER_ID = "U10002";
    private static final String TOKEN = "test-internal-token";
    private static final String CART_ID = "C-INTERNAL-1";
    private static final String ITEM_ID = "I-INTERNAL-1";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() {
        jdbcTemplate.update("DELETE FROM cart_item");
        jdbcTemplate.update("DELETE FROM cart");
        jdbcTemplate.update("INSERT INTO cart (cart_id, user_id) VALUES (?, ?)", CART_ID, USER_ID);
        jdbcTemplate.update("INSERT INTO cart_item (cart_item_id, cart_id, user_id, product_id, sku_id, quantity) "
                        + "VALUES (?, ?, ?, ?, ?, ?)",
                ITEM_ID, CART_ID, USER_ID, "P30001", "S40001", 2);
    }

    @Test
    void rejectsMissingToken() throws Exception {
        mockMvc.perform(get("/internal/cart").header("X-User-Id", USER_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(10003));
    }

    @Test
    void rejectsWrongToken() throws Exception {
        mockMvc.perform(get("/internal/cart")
                        .header("X-User-Id", USER_ID)
                        .header("X-Internal-Token", "wrong-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(10003));
    }

    @Test
    void acceptsValidTokenForEmptyCart() throws Exception {
        mockMvc.perform(get("/internal/cart")
                        .header("X-User-Id", EMPTY_USER_ID)
                        .header("X-Internal-Token", TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.items.length()").value(0));
    }

    @Test
    void removesItemWithValidToken() throws Exception {
        mockMvc.perform(delete("/internal/cart/items/{id}", ITEM_ID)
                        .header("X-User-Id", USER_ID)
                        .header("X-Internal-Token", TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));

        Integer deleted = jdbcTemplate.queryForObject(
                "SELECT is_deleted FROM cart_item WHERE cart_item_id = ?", Integer.class, ITEM_ID);
        assertEquals(1, deleted);
    }

    @Test
    void rejectsDeleteWithoutToken() throws Exception {
        mockMvc.perform(delete("/internal/cart/items/{id}", ITEM_ID)
                        .header("X-User-Id", USER_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(10003));

        Integer deleted = jdbcTemplate.queryForObject(
                "SELECT is_deleted FROM cart_item WHERE cart_item_id = ?", Integer.class, ITEM_ID);
        assertEquals(0, deleted);
    }
}
