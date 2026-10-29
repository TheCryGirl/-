package com.mall.payment.controller;

import com.mall.payment.client.OrderClient;
import com.mall.payment.client.ProductClient;
import com.mall.payment.client.dto.OrderDTO;
import com.mall.payment.common.OrderStatus;
import com.mall.payment.common.Result;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class PaymentPathIntegrationTest {

    private static final String BUYER_ID = "U1";
    private static final String PAYMENT_ID = "PAYTEST0000000000009";
    private static final String REFUND_ID = "REFTEST0000000000009";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @MockBean
    private OrderClient orderClient;

    @MockBean
    private ProductClient productClient;

    @BeforeEach
    void cleanDatabase() {
        jdbcTemplate.update("DELETE FROM after_sale_ticket");
        jdbcTemplate.update("DELETE FROM refund");
        jdbcTemplate.update("DELETE FROM payment_callback");
        jdbcTemplate.update("DELETE FROM payment");
    }

    @Test
    void apiAliasesServePaymentAndRefundList() throws Exception {
        insertPayment();
        insertRefund();

        mockMvc.perform(get("/api/payments/" + PAYMENT_ID)
                        .header("X-User-Id", BUYER_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.paymentId").value(PAYMENT_ID))
                .andExpect(jsonPath("$.data.status").value("SUCCESS"));

        mockMvc.perform(get("/payments/" + PAYMENT_ID)
                        .header("X-User-Id", BUYER_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));

        mockMvc.perform(get("/api/payments/refunds")
                        .param("status", "PENDING")
                        .header("X-User-Id", "SELLER01")
                        .header("X-User-Role", "SELLER"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.page").value(1))
                .andExpect(jsonPath("$.data.size").value(20))
                .andExpect(jsonPath("$.data.list[0].refundId").value(REFUND_ID))
                .andExpect(jsonPath("$.data.list[0].buyerId").value(BUYER_ID));

        mockMvc.perform(get("/api/payments/refunds")
                        .param("status", "PENDING")
                        .header("X-User-Id", "U9")
                        .header("X-User-Role", "BUYER"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(0));

        mockMvc.perform(get("/api/payments/refunds/" + REFUND_ID)
                        .header("X-User-Id", "SELLER01")
                        .header("X-User-Role", "SELLER"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.refundId").value(REFUND_ID))
                .andExpect(jsonPath("$.data.sellerId").value("SELLER01"));

        mockMvc.perform(get("/api/payments/refunds/" + REFUND_ID)
                        .header("X-User-Id", "SELLER99")
                        .header("X-User-Role", "SELLER"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(10003));
    }

    @Test
    void afterSaleEndpointsAreRoleFiltered() throws Exception {
        insertAfterSaleTicket();

        mockMvc.perform(get("/api/payments/after-sales")
                        .header("X-User-Id", BUYER_ID)
                        .header("X-User-Role", "BUYER"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.total").value(1));

        mockMvc.perform(get("/api/payments/after-sales")
                        .header("X-User-Id", "SELLER01")
                        .header("X-User-Role", "SELLER"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1));

        mockMvc.perform(get("/api/payments/after-sales")
                        .header("X-User-Id", "SELLER99")
                        .header("X-User-Role", "SELLER"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(0));

        mockMvc.perform(get("/api/payments/after-sales")
                        .header("X-User-Id", "ADMIN01")
                        .header("X-User-Role", "ADMIN"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1));

        mockMvc.perform(get("/api/payments/after-sales/ASTEST0000000000001")
                        .header("X-User-Id", "SELLER99")
                        .header("X-User-Role", "SELLER"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(10003));

        mockMvc.perform(get("/api/payments/after-sales/ASTEST0000000000001")
                        .header("X-User-Id", "SELLER01")
                        .header("X-User-Role", "SELLER"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.ticketId").value("ASTEST0000000000001"));
    }

    private void insertAfterSaleTicket() {
        jdbcTemplate.update("""
                INSERT INTO after_sale_ticket (
                    ticket_id, order_id, payment_id, buyer_id, seller_id, after_sale_type,
                    amount, reason, status, idempotency_key, active_flag,
                    created_by, updated_by, is_deleted
                ) VALUES ('ASTEST0000000000001', 'O50009', ?, ?, 'SELLER01', 'REFUND_ONLY',
                          5000, '路径测试', 'PENDING', 'after-sale-path-key', 1, ?, ?, 0)
                """, PAYMENT_ID, BUYER_ID, BUYER_ID, BUYER_ID);
    }

    @Test
    void apiAliasCreatesPaymentUsingOrderAmount() throws Exception {
        when(orderClient.getOrder("O50001", BUYER_ID))
                .thenReturn(Result.success(order()));

        mockMvc.perform(post("/api/payments")
                        .header("X-User-Id", BUYER_ID)
                        .header("Idempotency-Key", "path-test-key")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"orderId\":\"O50001\",\"channel\":\"MOCK\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.amount").value(11800));
    }

    private void insertPayment() {
        jdbcTemplate.update("""
                INSERT INTO payment (
                    payment_id, pay_no, order_id, buyer_id, seller_id, channel, amount,
                    refunded_amount, status, paid_at, created_by, updated_by, is_deleted
                ) VALUES (?, 'P20261020000009', 'O50009', ?, 'SELLER01', 'MOCK', 11800, 5000,
                          'SUCCESS', CURRENT_TIMESTAMP, ?, ?, 0)
                """, PAYMENT_ID, BUYER_ID, BUYER_ID, BUYER_ID);
    }

    private void insertRefund() {
        jdbcTemplate.update("""
                INSERT INTO refund (
                    refund_id, payment_id, order_id, buyer_id, seller_id, amount, reason,
                    status, created_by, updated_by, is_deleted
                ) VALUES (?, ?, 'O50009', ?, 'SELLER01', 5000, '路径测试', 'PENDING', ?, ?, 0)
                """, REFUND_ID, PAYMENT_ID, BUYER_ID, BUYER_ID, BUYER_ID);
    }

    private OrderDTO order() {
        OrderDTO order = new OrderDTO();
        order.setOrderId("O50001");
        order.setOrderNo("2026102012345678901");
        order.setStatus(OrderStatus.PENDING_PAYMENT);
        order.setBuyerId(BUYER_ID);
        order.setSellerId("S10001");
        order.setPayAmount(11800L);
        return order;
    }
}
