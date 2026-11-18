package com.mall.payment.controller;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import java.sql.Timestamp;
import java.time.LocalDateTime;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 结算接口路径与角色校验（#144 第二块）。
 *
 * <p>校验：/api/settlements 前缀可用；未登录 10002；买家 10003；
 * 生成与标记打款仅 ADMIN（卖家 10003）；卖家只能看到自己的结算单。
 */
@SpringBootTest
@AutoConfigureMockMvc
class SettlementControllerPermissionTest {

    private static final String SELLER_A = "S1";
    private static final String SELLER_B = "S2";
    private static final String PERIOD = "2026-11";
    /** payment-service 的 ServiceIngressFilter 校验的内部令牌，与测试配置一致 */
    private static final String INTERNAL_TOKEN = "test-internal-token";

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void cleanDatabase() {
        jdbcTemplate.update("DELETE FROM settlement_item");
        jdbcTemplate.update("DELETE FROM settlement");
        jdbcTemplate.update("DELETE FROM commission_rule");
        jdbcTemplate.update("DELETE FROM refund");
        jdbcTemplate.update("DELETE FROM mall_order.`order`");
    }

    @Test
    @DisplayName("未登录访问结算列表返回 10002")
    void listWithoutLoginIsUnauthorized() throws Exception {
        mockMvc.perform(get("/api/settlements")
                        .header("X-Internal-Token", INTERNAL_TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(10002));
    }

    @Test
    @DisplayName("买家角色访问结算列表返回 10003")
    void listAsBuyerIsForbidden() throws Exception {
        mockMvc.perform(get("/api/settlements")
                        .header("X-Internal-Token", INTERNAL_TOKEN)
                        .header("X-User-Id", "B1")
                        .header("X-User-Role", "BUYER"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(10003));
    }

    @Test
    @DisplayName("卖家只能看到自己的结算单，指定他人 sellerId 返回 10003")
    void sellerSeesOnlyOwnSettlements() throws Exception {
        seedOrders();

        mockMvc.perform(post("/api/settlements/generate")
                        .header("X-Internal-Token", INTERNAL_TOKEN)
                        .header("X-User-Id", "ADMIN01")
                        .header("X-User-Role", "ADMIN")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"period\":\"" + PERIOD + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));

        mockMvc.perform(get("/api/settlements")
                        .header("X-Internal-Token", INTERNAL_TOKEN)
                        .header("X-User-Id", SELLER_A)
                        .header("X-User-Role", "SELLER"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.list[0].sellerId").value(SELLER_A));

        mockMvc.perform(get("/api/settlements")
                        .header("X-Internal-Token", INTERNAL_TOKEN)
                        .header("X-User-Id", SELLER_A)
                        .header("X-User-Role", "SELLER")
                        .param("sellerId", SELLER_B))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(10003));
    }

    @Test
    @DisplayName("生成结算单仅管理员：卖家 10003，管理员成功")
    void generateRequiresAdmin() throws Exception {
        seedOrders();

        mockMvc.perform(post("/api/settlements/generate")
                        .header("X-Internal-Token", INTERNAL_TOKEN)
                        .header("X-User-Id", SELLER_A)
                        .header("X-User-Role", "SELLER")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"period\":\"" + PERIOD + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(10003));

        mockMvc.perform(post("/api/settlements/generate")
                        .header("X-Internal-Token", INTERNAL_TOKEN)
                        .header("X-User-Id", "ADMIN01")
                        .header("X-User-Role", "ADMIN")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"period\":\"" + PERIOD + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.created").value(2));
    }

    @Test
    @DisplayName("标记打款仅管理员：卖家 10003，管理员对不存在结算单 60015")
    void updateStatusRequiresAdmin() throws Exception {
        mockMvc.perform(put("/api/settlements/ST-ANY/status")
                        .header("X-Internal-Token", INTERNAL_TOKEN)
                        .header("X-User-Id", SELLER_A)
                        .header("X-User-Role", "SELLER")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"PAID\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(10003));

        mockMvc.perform(put("/api/settlements/ST-NOT-EXISTS/status")
                        .header("X-Internal-Token", INTERNAL_TOKEN)
                        .header("X-User-Id", "ADMIN01")
                        .header("X-User-Role", "ADMIN")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"PAID\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(60015));
    }

    private void seedOrders() {
        insertOrder("O1", SELLER_A, 10000L);
        insertOrder("O2", SELLER_B, 20000L);
    }

    private void insertOrder(String orderId, String sellerId, long payAmount) {
        jdbcTemplate.update("INSERT INTO mall_order.`order`"
                        + " (order_id, seller_id, pay_amount, status, updated_at, is_deleted)"
                        + " VALUES (?, ?, ?, 'COMPLETED', ?, 0)",
                orderId, sellerId, payAmount,
                Timestamp.valueOf(LocalDateTime.of(2026, 11, 5, 10, 0, 0)));
    }
}
