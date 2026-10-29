package com.mall.payment.service;

import com.mall.payment.client.OrderClient;
import com.mall.payment.client.ProductClient;
import com.mall.payment.client.dto.ConfirmStockResponse;
import com.mall.payment.client.dto.OrderDTO;
import com.mall.payment.client.dto.StockConfirmRequest;
import com.mall.payment.common.ErrorCode;
import com.mall.payment.common.OrderStatus;
import com.mall.payment.common.Result;
import com.mall.payment.dto.request.PaymentCallbackRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@SpringBootTest
class PaymentCallbackIntegrationTest {

    private static final String PAYMENT_ID = "PAYTEST0000000000001";
    private static final String PAY_NO = "P20261020000001";
    private static final String ORDER_ID = "O50001";
    private static final String BUYER_ID = "U10001";
    private static final long AMOUNT = 11800L;

    @Autowired
    private PaymentCallbackService callbackService;

    @Autowired
    private SignatureService signatureService;

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
        insertPendingPayment();
    }

    @Test
    void validCallbackMarksPaymentSuccessAndNotifiesOrderAndStock() {
        stubNormalOrder();

        CallbackResult result = callbackService.process(callback("T-VALID", AMOUNT));

        assertTrue(result.isSuccess());
        assertTrue(result.isNewlyPaid());
        assertFalse(result.isDuplicate());
        assertEquals("SUCCESS", paymentStatus());
        assertEquals(1, count("payment_callback"));
        assertEquals("PROCESSED", callbackStatus("T-VALID"));
        verify(orderClient, times(1)).markPaid(ORDER_ID);
        verify(orderClient, never()).markRefunded(ORDER_ID);
        verify(productClient, times(1)).confirmStock(any(StockConfirmRequest.class));
    }

    @Test
    void duplicateCallbackIsDeduplicatedByTradeNo() {
        stubNormalOrder();

        CallbackResult first = callbackService.process(callback("T-DUP", AMOUNT));
        CallbackResult second = callbackService.process(callback("T-DUP", AMOUNT));

        assertTrue(first.isNewlyPaid());
        assertTrue(second.isSuccess());
        assertTrue(second.isDuplicate());
        assertEquals(1, count("payment_callback"));
        assertEquals("SUCCESS", paymentStatus());
        verify(orderClient, times(1)).markPaid(ORDER_ID);
        verify(productClient, times(1)).confirmStock(any(StockConfirmRequest.class));
    }

    @Test
    void invalidSignatureRejectsWithoutChangingAnyState() {
        stubNormalOrder();
        PaymentCallbackRequest request = callback("T-BAD-SIGN", AMOUNT);
        request.setSign("bad-sign");

        CallbackResult result = callbackService.process(request);

        assertFalse(result.isSuccess());
        assertEquals(ErrorCode.SIGN_VERIFY_FAILED, result.getCode());
        assertEquals("PENDING", paymentStatus());
        assertEquals(0, count("payment_callback"));
        verifyNoInteractions(orderClient, productClient);
    }

    @Test
    void amountMismatchIsAlertedAndRejected() {
        stubNormalOrder();
        long callbackAmount = 10000L;

        CallbackResult result = callbackService.process(callback("T-AMOUNT", callbackAmount));

        assertFalse(result.isSuccess());
        assertEquals(ErrorCode.AMOUNT_MISMATCH, result.getCode());
        assertEquals("PENDING", paymentStatus());
        assertEquals(1, count("payment_callback"));
        assertEquals("AMOUNT_MISMATCH", callbackStatus("T-AMOUNT"));
        verifyNoInteractions(orderClient, productClient);
    }

    @Test
    void unknownPaymentIsRejectedWithoutCallbackRecord() {
        PaymentCallbackRequest request = callback("T-UNKNOWN", AMOUNT);
        request.setPayNo("P-NOT-EXISTS");
        request.setSign(signatureService.sign(request.getTradeNo(), request.getPayNo(), request.getAmount()));

        CallbackResult result = callbackService.process(request);

        assertFalse(result.isSuccess());
        assertEquals(ErrorCode.PAYMENT_NOT_FOUND, result.getCode());
        assertEquals(0, count("payment_callback"));
        verifyNoInteractions(orderClient, productClient);
    }

    @Test
    void cancelledOrderTriggersAutomaticRefund() {
        when(orderClient.getOrder(ORDER_ID, BUYER_ID))
                .thenReturn(Result.success(order(OrderStatus.CANCELLED)));

        CallbackResult result = callbackService.process(callback("T-CANCELLED", AMOUNT));

        assertTrue(result.isSuccess());
        assertEquals("REFUNDED", paymentStatus());
        assertEquals(AMOUNT, paymentRefundedAmount());
        assertEquals(1, count("refund"));
        assertEquals("SUCCESS", refundStatus());
        verify(orderClient, never()).markPaid(ORDER_ID);
        verify(orderClient, never()).markRefunded(ORDER_ID);
        verify(productClient, never()).confirmStock(any(StockConfirmRequest.class));
    }

    @Test
    void refundedOrderTriggersAutomaticRefund() {
        when(orderClient.getOrder(ORDER_ID, BUYER_ID))
                .thenReturn(Result.success(order(OrderStatus.REFUNDED)));

        CallbackResult result = callbackService.process(callback("T-ORDER-REFUNDED", AMOUNT));

        assertTrue(result.isSuccess());
        assertEquals("REFUNDED", paymentStatus());
        assertEquals(AMOUNT, paymentRefundedAmount());
        assertEquals(1, count("refund"));
        assertEquals("SUCCESS", refundStatus());
        verify(orderClient, never()).markPaid(ORDER_ID);
        verify(orderClient, never()).markRefunded(ORDER_ID);
        verify(productClient, never()).confirmStock(any(StockConfirmRequest.class));
    }

    @Test
    void concurrentDuplicateCallbacksOnlyProcessOnce() throws Exception {
        stubNormalOrder();

        int threads = 6;
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        CountDownLatch ready = new CountDownLatch(threads);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<CallbackResult>> futures = new ArrayList<>();
        try {
            for (int i = 0; i < threads; i++) {
                futures.add(executor.submit(() -> {
                    ready.countDown();
                    start.await(5, TimeUnit.SECONDS);
                    return callbackService.process(callback("T-CONCURRENT", AMOUNT));
                }));
            }
            assertTrue(ready.await(5, TimeUnit.SECONDS));
            start.countDown();

            int newlyPaidCount = 0;
            for (Future<CallbackResult> future : futures) {
                CallbackResult result = future.get(15, TimeUnit.SECONDS);
                assertTrue(result.isSuccess());
                if (result.isNewlyPaid()) {
                    newlyPaidCount++;
                }
            }
            assertEquals(1, newlyPaidCount);
        } finally {
            executor.shutdownNow();
        }

        assertEquals(1, count("payment_callback"));
        assertEquals("SUCCESS", paymentStatus());
        verify(orderClient, times(1)).markPaid(ORDER_ID);
        verify(productClient, times(1)).confirmStock(any(StockConfirmRequest.class));
    }

    private void insertPendingPayment() {
        jdbcTemplate.update("""
                INSERT INTO payment (
                    payment_id, pay_no, order_id, buyer_id, channel, amount,
                    refunded_amount, status, created_by, updated_by, is_deleted
                ) VALUES (?, ?, ?, ?, 'MOCK', ?, 0, 'PENDING', ?, ?, 0)
                """, PAYMENT_ID, PAY_NO, ORDER_ID, BUYER_ID, AMOUNT, BUYER_ID, BUYER_ID);
    }

    private PaymentCallbackRequest callback(String tradeNo, long amount) {
        PaymentCallbackRequest request = new PaymentCallbackRequest();
        request.setTradeNo(tradeNo);
        request.setPayNo(PAY_NO);
        request.setAmount(amount);
        request.setStatus("SUCCESS");
        request.setPayTime("2026-10-20 22:00:00");
        request.setSign(signatureService.sign(tradeNo, PAY_NO, amount));
        return request;
    }

    private void stubNormalOrder() {
        when(orderClient.getOrder(ORDER_ID, BUYER_ID))
                .thenReturn(Result.success(order(OrderStatus.PENDING_PAYMENT)));
        when(orderClient.markPaid(ORDER_ID)).thenReturn(Result.success(null));
        when(productClient.confirmStock(any(StockConfirmRequest.class)))
                .thenReturn(Result.success(new ConfirmStockResponse(true)));
    }

    private OrderDTO order(OrderStatus status) {
        OrderDTO order = new OrderDTO();
        order.setOrderId(ORDER_ID);
        order.setOrderNo("2026102012345678901");
        order.setStatus(status);
        order.setBuyerId(BUYER_ID);
        order.setPayAmount(AMOUNT);
        return order;
    }

    private String paymentStatus() {
        return jdbcTemplate.queryForObject(
                "SELECT status FROM payment WHERE payment_id = ?", String.class, PAYMENT_ID);
    }

    private long paymentRefundedAmount() {
        Long value = jdbcTemplate.queryForObject(
                "SELECT refunded_amount FROM payment WHERE payment_id = ?", Long.class, PAYMENT_ID);
        return value == null ? 0 : value;
    }

    private String callbackStatus(String tradeNo) {
        return jdbcTemplate.queryForObject(
                "SELECT process_status FROM payment_callback WHERE trade_no = ?", String.class, tradeNo);
    }

    private String refundStatus() {
        return jdbcTemplate.queryForObject(
                "SELECT status FROM refund WHERE payment_id = ?", String.class, PAYMENT_ID);
    }

    private int count(String table) {
        Integer count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM " + table, Integer.class);
        return count == null ? 0 : count;
    }
}
