package com.mall.payment.service;

import com.mall.payment.client.OrderClient;
import com.mall.payment.client.ProductClient;
import com.mall.payment.client.dto.ConfirmStockResponse;
import com.mall.payment.client.dto.MainOrderDTO;
import com.mall.payment.client.dto.OrderDTO;
import com.mall.payment.client.dto.StockConfirmRequest;
import com.mall.payment.common.BizException;
import com.mall.payment.common.ErrorCode;
import com.mall.payment.common.OrderStatus;
import com.mall.payment.common.PaymentChannel;
import com.mall.payment.common.PaymentStatus;
import com.mall.payment.common.Result;
import com.mall.payment.dto.request.CreatePaymentRequest;
import com.mall.payment.dto.request.PaymentCallbackRequest;
import com.mall.payment.dto.response.CreatePaymentResponse;
import com.mall.payment.entity.Payment;
import com.mall.payment.mapper.PaymentMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Issue #135：跨店合并结算的支付链路。
 *
 * <p>支付单按主订单创建（order_id 存主订单ID），金额用主订单 payAmount；
 * 回调成功后逐个子订单通知已支付并确认库存；主订单取消时整笔自动退款。
 */
@SpringBootTest
class Issue135MainOrderPaymentTest {

    private static final String BUYER_ID = "U135-BUYER";
    private static final String MAIN_ORDER_ID = "M202111111111160001abcdef";
    private static final String SUB_A = "O202111111111160001aaaaaa";
    private static final String SUB_B = "O202111111111160001bbbbbb";
    private static final String SELLER_A = "U135-SELLER-A";
    private static final String SELLER_B = "U135-SELLER-B";
    private static final long MAIN_AMOUNT = 19000L;

    @Autowired
    private PaymentService paymentService;

    @Autowired
    private PaymentCallbackService callbackService;

    @Autowired
    private SignatureService signatureService;

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
        jdbcTemplate.update("DELETE FROM refund_notification_task");
        jdbcTemplate.update("DELETE FROM after_sale_ticket");
        jdbcTemplate.update("DELETE FROM refund");
        jdbcTemplate.update("DELETE FROM payment_callback");
        jdbcTemplate.update("DELETE FROM payment");
    }

    @Test
    void createsPaymentFromMainOrderAmount() {
        when(orderClient.getMainOrder(MAIN_ORDER_ID, BUYER_ID))
                .thenReturn(Result.success(mainOrder(OrderStatus.PENDING_PAYMENT)));

        CreatePaymentResponse response = paymentService.createPayment(
                BUYER_ID, "key-135-1", request());

        assertEquals(MAIN_AMOUNT, response.getAmount());
        Payment saved = paymentMapper.selectById(response.getPaymentId());
        assertEquals(PaymentStatus.PENDING, saved.getStatus());
        assertEquals(MAIN_ORDER_ID, saved.getOrderId());
        assertEquals(BUYER_ID, saved.getBuyerId());
        // 跨店支付单没有单一卖家
        assertNull(saved.getSellerId());
        assertEquals(1, count("payment"));
        verify(orderClient, times(1)).getMainOrder(MAIN_ORDER_ID, BUYER_ID);
        verify(orderClient, never()).getOrder(anyString(), anyString());
    }

    @Test
    void sameKeyReturnsSamePaymentWithoutQueryingAgain() {
        when(orderClient.getMainOrder(MAIN_ORDER_ID, BUYER_ID))
                .thenReturn(Result.success(mainOrder(OrderStatus.PENDING_PAYMENT)));

        CreatePaymentResponse first = paymentService.createPayment(BUYER_ID, "key-135-1", request());
        CreatePaymentResponse second = paymentService.createPayment(BUYER_ID, "key-135-1", request());

        assertEquals(first.getPaymentId(), second.getPaymentId());
        assertEquals(1, count("payment"));
        verify(orderClient, times(1)).getMainOrder(MAIN_ORDER_ID, BUYER_ID);
    }

    @Test
    void rejectsPaidMainOrder() {
        when(orderClient.getMainOrder(MAIN_ORDER_ID, BUYER_ID))
                .thenReturn(Result.success(mainOrder(OrderStatus.PAID)));

        BizException exception = assertThrows(BizException.class,
                () -> paymentService.createPayment(BUYER_ID, "key-135-1", request()));
        assertEquals(ErrorCode.PARAM_INVALID, exception.getCode());
        assertEquals(0, count("payment"));
    }

    @Test
    void rejectsOtherBuyersMainOrder() {
        MainOrderDTO mainOrder = mainOrder(OrderStatus.PENDING_PAYMENT);
        mainOrder.setBuyerId("U-OTHER");
        when(orderClient.getMainOrder(MAIN_ORDER_ID, BUYER_ID)).thenReturn(Result.success(mainOrder));

        BizException exception = assertThrows(BizException.class,
                () -> paymentService.createPayment(BUYER_ID, "key-135-1", request()));
        assertEquals(ErrorCode.FORBIDDEN, exception.getCode());
        assertEquals(0, count("payment"));
    }

    @Test
    void callbackNotifiesEverySubOrderAndConfirmsStockPerSubOrder() {
        when(orderClient.getMainOrder(MAIN_ORDER_ID, BUYER_ID))
                .thenReturn(Result.success(mainOrder(OrderStatus.PENDING_PAYMENT)));
        CreatePaymentResponse created = paymentService.createPayment(BUYER_ID, "key-135-1", request());
        Payment payment = paymentMapper.selectById(created.getPaymentId());

        when(orderClient.getMainOrder(MAIN_ORDER_ID, BUYER_ID))
                .thenReturn(Result.success(mainOrder(OrderStatus.PENDING_PAYMENT)));
        when(orderClient.markPaid(SUB_A)).thenReturn(Result.success(null));
        when(orderClient.markPaid(SUB_B)).thenReturn(Result.success(null));
        when(productClient.confirmStock(any(StockConfirmRequest.class)))
                .thenReturn(Result.success(new ConfirmStockResponse(true)));

        CallbackResult result = callbackService.process(callback(payment.getPayNo(), MAIN_AMOUNT, "T-135-1"));

        assertTrue(result.isSuccess());
        assertTrue(result.isNewlyPaid());
        assertEquals("SUCCESS", paymentStatus(payment.getPaymentId()));
        verify(orderClient, times(1)).markPaid(SUB_A);
        verify(orderClient, times(1)).markPaid(SUB_B);
        verify(productClient, times(1)).confirmStock(new StockConfirmRequest(SUB_A));
        verify(productClient, times(1)).confirmStock(new StockConfirmRequest(SUB_B));
        // 对账完成
        assertTrue(jdbcTemplate.queryForObject(
                "SELECT reconciled_at FROM payment WHERE payment_id = ?", Object.class,
                payment.getPaymentId()) != null);
    }

    @Test
    void cancelledMainOrderTriggersAutoRefundWithoutNotifyingSubOrders() {
        when(orderClient.getMainOrder(MAIN_ORDER_ID, BUYER_ID))
                .thenReturn(Result.success(mainOrder(OrderStatus.PENDING_PAYMENT)));
        CreatePaymentResponse created = paymentService.createPayment(BUYER_ID, "key-135-1", request());
        Payment payment = paymentMapper.selectById(created.getPaymentId());

        when(orderClient.getMainOrder(MAIN_ORDER_ID, BUYER_ID))
                .thenReturn(Result.success(mainOrder(OrderStatus.CANCELLED)));

        CallbackResult result = callbackService.process(callback(payment.getPayNo(), MAIN_AMOUNT, "T-135-2"));

        assertTrue(result.isSuccess());
        assertEquals("REFUNDED", paymentStatus(payment.getPaymentId()));
        assertEquals(MAIN_AMOUNT, paymentRefundedAmount(payment.getPaymentId()));
        verify(orderClient, never()).markPaid(anyString());
        verify(productClient, never()).confirmStock(any(StockConfirmRequest.class));
        // 整笔自动退款保留主订单ID，不触发子订单状态变更
        assertEquals(1, count("refund"));
        assertEquals(MAIN_ORDER_ID, jdbcTemplate.queryForObject(
                "SELECT order_id FROM refund WHERE payment_id = ?", String.class, payment.getPaymentId()));
    }

    private CreatePaymentRequest request() {
        CreatePaymentRequest request = new CreatePaymentRequest();
        request.setOrderId(MAIN_ORDER_ID);
        request.setChannel(PaymentChannel.MOCK);
        return request;
    }

    private MainOrderDTO mainOrder(OrderStatus status) {
        MainOrderDTO mainOrder = new MainOrderDTO();
        mainOrder.setMainOrderId(MAIN_ORDER_ID);
        mainOrder.setMainOrderNo("202111111111160001");
        mainOrder.setBuyerId(BUYER_ID);
        mainOrder.setStatus(status);
        mainOrder.setPayAmount(MAIN_AMOUNT);
        mainOrder.setSubOrders(List.of(subOrder(SUB_A, SELLER_A, 5000L), subOrder(SUB_B, SELLER_B, 14000L)));
        return mainOrder;
    }

    private OrderDTO subOrder(String orderId, String sellerId, long payAmount) {
        OrderDTO order = new OrderDTO();
        order.setOrderId(orderId);
        order.setOrderNo("NO-" + orderId);
        order.setStatus(OrderStatus.PENDING_PAYMENT);
        order.setBuyerId(BUYER_ID);
        order.setSellerId(sellerId);
        order.setPayAmount(payAmount);
        order.setMainOrderId(MAIN_ORDER_ID);
        return order;
    }

    private PaymentCallbackRequest callback(String payNo, long amount, String tradeNo) {
        PaymentCallbackRequest request = new PaymentCallbackRequest();
        request.setTradeNo(tradeNo);
        request.setPayNo(payNo);
        request.setAmount(amount);
        request.setStatus("SUCCESS");
        request.setPayTime("2026-11-17 16:00:00");
        request.setSign(signatureService.sign(tradeNo, payNo, amount));
        return request;
    }

    private String paymentStatus(String paymentId) {
        return jdbcTemplate.queryForObject(
                "SELECT status FROM payment WHERE payment_id = ?", String.class, paymentId);
    }

    private long paymentRefundedAmount(String paymentId) {
        Long value = jdbcTemplate.queryForObject(
                "SELECT refunded_amount FROM payment WHERE payment_id = ?", Long.class, paymentId);
        return value == null ? 0 : value;
    }

    private int count(String table) {
        Integer count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM " + table, Integer.class);
        return count == null ? 0 : count;
    }
}