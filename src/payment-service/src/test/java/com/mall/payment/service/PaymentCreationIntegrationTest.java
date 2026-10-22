package com.mall.payment.service;

import com.mall.payment.client.OrderClient;
import com.mall.payment.client.ProductClient;
import com.mall.payment.client.dto.OrderDTO;
import com.mall.payment.common.BizException;
import com.mall.payment.common.ErrorCode;
import com.mall.payment.common.OrderStatus;
import com.mall.payment.common.PaymentChannel;
import com.mall.payment.common.PaymentStatus;
import com.mall.payment.common.Result;
import com.mall.payment.dto.request.CreatePaymentRequest;
import com.mall.payment.dto.response.CreatePaymentResponse;
import com.mall.payment.entity.Payment;
import com.mall.payment.mapper.PaymentMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@SpringBootTest
class PaymentCreationIntegrationTest {

    private static final String BUYER_ID = "U10001";
    private static final String ORDER_ID = "O50001";

    @Autowired
    private PaymentService paymentService;

    @Autowired
    private PaymentMapper paymentMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @MockBean
    private OrderClient orderClient;

    @MockBean
    private ProductClient productClient;

    @BeforeEach
    void cleanDatabase() {
        jdbcTemplate.update("DELETE FROM refund");
        jdbcTemplate.update("DELETE FROM payment_callback");
        jdbcTemplate.update("DELETE FROM payment");
    }

    @Test
    void createsPaymentUsingAmountFromOrder() {
        when(orderClient.getOrder(ORDER_ID, BUYER_ID))
                .thenReturn(Result.success(order(11800L, OrderStatus.PENDING_PAYMENT, BUYER_ID)));

        CreatePaymentResponse response = paymentService.createPayment(BUYER_ID, "key-1", request(PaymentChannel.MOCK));

        assertEquals(11800L, response.getAmount());
        assertEquals("mock://pay/" + response.getPaymentId(), response.getPayUrl());

        Payment saved = paymentMapper.selectById(response.getPaymentId());
        assertEquals(PaymentStatus.PENDING, saved.getStatus());
        assertEquals(0L, saved.getRefundedAmount());
        assertEquals(BUYER_ID, saved.getBuyerId());
        assertEquals(1, count("payment"));
        verify(orderClient, times(1)).getOrder(ORDER_ID, BUYER_ID);
        verifyNoInteractions(productClient);
    }

    @Test
    void sameIdempotencyKeyReturnsSamePaymentWithoutQueryingOrderAgain() {
        when(orderClient.getOrder(ORDER_ID, BUYER_ID))
                .thenReturn(Result.success(order(11800L, OrderStatus.PENDING_PAYMENT, BUYER_ID)));

        CreatePaymentResponse first = paymentService.createPayment(BUYER_ID, "key-1", request(PaymentChannel.MOCK));
        CreatePaymentResponse second = paymentService.createPayment(BUYER_ID, "key-1", request(PaymentChannel.MOCK));

        assertEquals(first.getPaymentId(), second.getPaymentId());
        assertEquals(1, count("payment"));
        verify(orderClient, times(1)).getOrder(ORDER_ID, BUYER_ID);
    }

    @Test
    void pendingPaymentIsReusedForSameOrderWithDifferentIdempotencyKey() {
        when(orderClient.getOrder(ORDER_ID, BUYER_ID))
                .thenReturn(Result.success(order(11800L, OrderStatus.PENDING_PAYMENT, BUYER_ID)));

        CreatePaymentResponse first = paymentService.createPayment(BUYER_ID, "key-1", request(PaymentChannel.MOCK));
        CreatePaymentResponse second = paymentService.createPayment(BUYER_ID, "key-2", request(PaymentChannel.MOCK));

        assertEquals(first.getPaymentId(), second.getPaymentId());
        assertEquals(1, count("payment"));
        verify(orderClient, times(1)).getOrder(ORDER_ID, BUYER_ID);
    }

    @Test
    void rejectsOrderOwnedByAnotherBuyer() {
        when(orderClient.getOrder(ORDER_ID, BUYER_ID))
                .thenReturn(Result.success(order(11800L, OrderStatus.PENDING_PAYMENT, "U20002")));

        BizException exception = assertThrows(BizException.class,
                () -> paymentService.createPayment(BUYER_ID, "key-1", request(PaymentChannel.MOCK)));

        assertEquals(ErrorCode.FORBIDDEN, exception.getCode());
        assertEquals(0, count("payment"));
    }

    @Test
    void rejectsOrderNotPendingPayment() {
        when(orderClient.getOrder(ORDER_ID, BUYER_ID))
                .thenReturn(Result.success(order(11800L, OrderStatus.PAID, BUYER_ID)));

        BizException exception = assertThrows(BizException.class,
                () -> paymentService.createPayment(BUYER_ID, "key-1", request(PaymentChannel.MOCK)));

        assertEquals(ErrorCode.PARAM_INVALID, exception.getCode());
        assertEquals(0, count("payment"));
    }

    @Test
    void rejectsUnsupportedChannelWithoutCallingOrderService() {
        CreatePaymentRequest request = request(PaymentChannel.ALIPAY);

        BizException exception = assertThrows(BizException.class,
                () -> paymentService.createPayment(BUYER_ID, "key-1", request));

        assertEquals(ErrorCode.PARAM_INVALID, exception.getCode());
        verifyNoInteractions(orderClient);
    }

    private CreatePaymentRequest request(PaymentChannel channel) {
        CreatePaymentRequest request = new CreatePaymentRequest();
        request.setOrderId(ORDER_ID);
        request.setChannel(channel);
        return request;
    }

    private OrderDTO order(long payAmount, OrderStatus status, String buyerId) {
        OrderDTO order = new OrderDTO();
        order.setOrderId(ORDER_ID);
        order.setOrderNo("2026102012345678901");
        order.setStatus(status);
        order.setBuyerId(buyerId);
        order.setPayAmount(payAmount);
        return order;
    }

    private int count(String table) {
        Integer count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM " + table, Integer.class);
        return count == null ? 0 : count;
    }
}
