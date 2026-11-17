package com.mall.order;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 物流接口的 HTTP 层验证（任务 #138）。
 *
 * <p>{@link LogisticsServiceIntegrationTest} 直接调 service 方法，证明的是业务规则；
 * 这里走完整的 MockMvc 链路（含过滤器、参数绑定、JSON 序列化、全局异常处理），
 * 证明的是「前端按契约发请求真的能拿到契约里写的响应」——
 * 两者都需要，缺一个就可能出现「service 测试全绿但接口 404/字段名不对」。
 *
 * <p>测试配置里的 `order.internal-token` 是 `test-internal-token`，
 * 所有请求都要带上，否则会被 ServiceIngressFilter 挡在 403。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class LogisticsApiTest {

    private static final String TOKEN = "test-internal-token";
    private static final String BUYER = "U13810001";
    private static final String SELLER = "U13810002";
    private static final String ORDER_ID = "OLOGAPI00001";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        jdbcTemplate.update("DELETE FROM order_logistics_track");
        jdbcTemplate.update("DELETE FROM order_compensation");
        jdbcTemplate.update("DELETE FROM order_idempotency");
        jdbcTemplate.update("DELETE FROM order_item");
        jdbcTemplate.update("DELETE FROM `order`");
        insertOrder("PAID");
    }

    @Test
    void shipWithoutExpressCompanyReturnsCode10001() throws Exception {
        mockMvc.perform(put("/api/orders/" + ORDER_ID + "/ship")
                        .header("X-Internal-Token", TOKEN)
                        .header("X-User-Id", SELLER)
                        .header("X-User-Role", "SELLER")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"trackingNo\":\"SF123456\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(10001));
    }

    @Test
    void shipWithoutTrackingNoReturnsCode10001() throws Exception {
        mockMvc.perform(put("/api/orders/" + ORDER_ID + "/ship")
                        .header("X-Internal-Token", TOKEN)
                        .header("X-User-Id", SELLER)
                        .header("X-User-Role", "SELLER")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"expressCompany\":\"SF\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(10001));
    }

    @Test
    void shipThenBuyerSeesTrackingNoAndEmptyTrackList() throws Exception {
        mockMvc.perform(put("/api/orders/" + ORDER_ID + "/ship")
                        .header("X-Internal-Token", TOKEN)
                        .header("X-User-Id", SELLER)
                        .header("X-User-Role", "SELLER")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"expressCompany\":\"SF\",\"trackingNo\":\"SF9988776655\"}"))
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.status").value("SHIPPED"))
                .andExpect(jsonPath("$.data.expressCompany").value("SF"))
                .andExpect(jsonPath("$.data.expressCompanyLabel").value("顺丰速运"))
                .andExpect(jsonPath("$.data.trackingNo").value("SF9988776655"));

        mockMvc.perform(get("/api/orders/" + ORDER_ID + "/logistics")
                        .header("X-Internal-Token", TOKEN)
                        .header("X-User-Id", BUYER)
                        .header("X-User-Role", "BUYER"))
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.shipped").value(true))
                .andExpect(jsonPath("$.data.trackingNo").value("SF9988776655"))
                .andExpect(jsonPath("$.data.tracks").isEmpty())
                .andExpect(jsonPath("$.data.message").value("已发货，物流更新中"));
    }

    @Test
    void unshippedOrderLogisticsReturnsEmptyListWithHintNotAnError() throws Exception {
        mockMvc.perform(get("/api/orders/" + ORDER_ID + "/logistics")
                        .header("X-Internal-Token", TOKEN)
                        .header("X-User-Id", BUYER)
                        .header("X-User-Role", "BUYER"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.shipped").value(false))
                .andExpect(jsonPath("$.data.tracks").isEmpty())
                .andExpect(jsonPath("$.data.message").value("订单尚未发货，暂无物流信息"));
    }

    @Test
    void threeTracksComeBackInChronologicalOrderWithAbnormalFlag() throws Exception {
        ship();
        appendTrack("OUT_FOR_DELIVERY", "派送中", "2026-11-15 09:00:00");
        appendTrack("COLLECTED", "已揽收", "2026-11-13 10:00:00");
        appendTrack("EXCEPTION", "地址异常", "2026-11-14 08:30:00");

        MvcResult result = mockMvc.perform(get("/api/orders/" + ORDER_ID + "/logistics")
                        .header("X-Internal-Token", TOKEN)
                        .header("X-User-Id", BUYER)
                        .header("X-User-Role", "BUYER"))
                .andExpect(jsonPath("$.code").value(0))
                .andReturn();

        // MockMvc 默认按 ISO-8859-1 解响应体，中文会变乱码；这里显式按 UTF-8 读
        JsonNode tracks = objectMapper.readTree(
                        result.getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8))
                .path("data").path("tracks");
        assertThat(tracks).hasSize(3);
        assertThat(tracks.get(0).path("status").asText()).isEqualTo("COLLECTED");
        assertThat(tracks.get(1).path("status").asText()).isEqualTo("EXCEPTION");
        assertThat(tracks.get(2).path("status").asText()).isEqualTo("OUT_FOR_DELIVERY");
        // 只有异常件 abnormal=true，前端据此标红
        assertThat(tracks.get(0).path("abnormal").asBoolean()).isFalse();
        assertThat(tracks.get(1).path("abnormal").asBoolean()).isTrue();
        assertThat(tracks.get(2).path("abnormal").asBoolean()).isFalse();
        // 中文状态名由后端给出，前端不必再维护一份
        assertThat(tracks.get(1).path("statusLabel").asText()).isEqualTo("异常件");
    }

    @Test
    void buyerCannotAppendTrackButAdminCan() throws Exception {
        ship();
        mockMvc.perform(post("/api/orders/" + ORDER_ID + "/logistics/tracks")
                        .header("X-Internal-Token", TOKEN)
                        .header("X-User-Id", BUYER)
                        .header("X-User-Role", "BUYER")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"IN_TRANSIT\",\"description\":\"买家乱录\"}"))
                .andExpect(jsonPath("$.code").value(10003));

        mockMvc.perform(post("/api/orders/" + ORDER_ID + "/logistics/tracks")
                        .header("X-Internal-Token", TOKEN)
                        .header("X-User-Id", "U13810009")
                        .header("X-User-Role", "ADMIN")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"IN_TRANSIT\",\"description\":\"管理员补录\"}"))
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.status").value("IN_TRANSIT"))
                .andExpect(jsonPath("$.data.abnormal").value(false));
    }

    @Test
    void unknownTrackStatusReturnsCode10001() throws Exception {
        ship();
        mockMvc.perform(post("/api/orders/" + ORDER_ID + "/logistics/tracks")
                        .header("X-Internal-Token", TOKEN)
                        .header("X-User-Id", SELLER)
                        .header("X-User-Role", "SELLER")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"DELIVERED\",\"description\":\"不存在的状态\"}"))
                .andExpect(jsonPath("$.code").value(10001));
    }

    @Test
    void missingInternalTokenIsRejectedBeforeReachingController() throws Exception {
        mockMvc.perform(get("/api/orders/" + ORDER_ID + "/logistics")
                        .header("X-User-Id", BUYER)
                        .header("X-User-Role", "BUYER"))
                .andExpect(status().isForbidden());
    }

    /* ---------- 辅助 ---------- */

    private void ship() throws Exception {
        mockMvc.perform(put("/api/orders/" + ORDER_ID + "/ship")
                        .header("X-Internal-Token", TOKEN)
                        .header("X-User-Id", SELLER)
                        .header("X-User-Role", "SELLER")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"expressCompany\":\"ZTO\",\"trackingNo\":\"ZTO123456\"}"))
                .andExpect(jsonPath("$.code").value(0));
    }

    private void appendTrack(String status, String description, String occurredAt) throws Exception {
        mockMvc.perform(post("/api/orders/" + ORDER_ID + "/logistics/tracks")
                        .header("X-Internal-Token", TOKEN)
                        .header("X-User-Id", SELLER)
                        .header("X-User-Role", "SELLER")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(java.util.Map.of(
                                "status", status,
                                "description", description,
                                "occurredAt", occurredAt))))
                .andExpect(jsonPath("$.code").value(0));
    }

    private void insertOrder(String status) {
        LocalDateTime now = LocalDateTime.now();
        jdbcTemplate.update("INSERT INTO `order` (order_id, order_no, buyer_id, seller_id, status, "
                        + "total_amount, pay_amount, receiver, receiver_phone, address_full, tracking_no, "
                        + "express_company, shipped_at, expire_at, paid_at, created_at, updated_at, "
                        + "created_by, updated_by, is_deleted, inventory_released) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 0, 0)",
                ORDER_ID, "NO" + ORDER_ID, BUYER, SELLER, status, 10000L, 10000L,
                "测试买家", "13800138000", "测试省 测试市 测试路 1 号", null,
                null, null, now.plusMinutes(30), null, now, now, BUYER, BUYER);
    }
}
