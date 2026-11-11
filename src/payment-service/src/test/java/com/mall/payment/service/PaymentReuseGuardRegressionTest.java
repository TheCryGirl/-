package com.mall.payment.service;

import com.mall.payment.client.OrderClient;
import com.mall.payment.common.BizException;
import com.mall.payment.common.ErrorCode;
import com.mall.payment.common.PaymentChannel;
import com.mall.payment.common.PaymentStatus;
import com.mall.payment.dto.request.CreatePaymentRequest;
import com.mall.payment.dto.response.CreatePaymentResponse;
import com.mall.payment.entity.Payment;
import com.mall.payment.mapper.PaymentMapper;
import com.mall.payment.service.impl.PaymentServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DuplicateKeyException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * 支付单「复用已有单」路径的越权与错配回归测试。
 *
 * <p>背景：创建支付单时，幂等键命中（selectByIdempotencyKey）和订单已有可用支付单
 * （selectLatestByOrderId）曾在校验 buyerId / orderId / channel 之前直接返回已有支付单，
 * 导致拿别人的幂等键、或拿同一订单号换渠道也能拿到别人的支付单号与支付链接；
 * {@link PaymentTxService} 在唯一键冲突后回读的三条分支存在同样的问题。
 * 本测试锁定修复后的行为：所有候选返回前都先过 {@link PaymentReuseGuard}。
 *
 * <p>规则：复用路径必须校验 existing.buyerId == 调用方、existing.orderId == 请求 orderId、
 * existing.channel == 请求渠道；buyer 缺失、owner 不同、order 不同、channel 不同都不复用，
 * 且不再继续回读其他候选单。同一调用方换幂等键复用同一订单的可用支付单属于正常场景，必须继续支持。
 *
 * <p>校验集中在 {@link PaymentReuseGuard}，两侧复用路径都先过它。
 *
 * <p>错误码：越权（买家缺失或不是调用方）沿用已有 {@link ErrorCode#FORBIDDEN}；
 * 订单或渠道错配沿用已有 {@link ErrorCode#PARAM_INVALID}，不新增错误码。
 * 所有拒绝都以 {@link BizException} 抛出，且不再继续回读其他候选单。
 *
 * <p>本类只用 Mockito，不加载 Spring 上下文，不访问数据库与下游服务。
 */
class PaymentReuseGuardRegressionTest {

    private static final String CALLER = "U10001";
    private static final String OTHER_BUYER = "U20002";
    private static final String ORDER_ID = "O50001";
    private static final String OTHER_ORDER_ID = "O50002";
    private static final String KEY = "key-1";
    private static final String OTHER_KEY = "key-2";
    private static final long AMOUNT = 11800L;

    private PaymentMapper paymentMapper;
    private OrderClient orderClient;
    private PaymentTxService paymentTxService;
    private PaymentServiceImpl paymentService;

    @BeforeEach
    void setUp() {
        paymentMapper = mock(PaymentMapper.class);
        orderClient = mock(OrderClient.class);
        paymentTxService = new PaymentTxService(paymentMapper);
        paymentService = new PaymentServiceImpl(paymentMapper, paymentTxService, orderClient);
    }

    // ---------- PaymentService：幂等键命中（byKey）----------

    @Nested
    @DisplayName("PaymentService 幂等键命中复用")
    class ByIdempotencyKey {

        @Test
        @DisplayName("他人用过同一幂等键：拒绝，不返回他人支付单，不查订单")
        void rejectsIdempotencyKeyOwnedByAnotherBuyer() {
            when(paymentMapper.selectByIdempotencyKey(KEY))
                    .thenReturn(payment("PAYREUSE00000000001", OTHER_BUYER, ORDER_ID,
                            PaymentChannel.MOCK, PaymentStatus.PENDING));

            BizException exception = assertRejected(
                    () -> paymentService.createPayment(CALLER, KEY, request(ORDER_ID, PaymentChannel.MOCK)));

            assertEquals(ErrorCode.FORBIDDEN, exception.getCode());
            verifyNoInteractions(orderClient);
        }

        @Test
        @DisplayName("同一幂等键换了订单：拒绝，不返回另一订单的支付单")
        void rejectsIdempotencyKeyReusedForDifferentOrder() {
            when(paymentMapper.selectByIdempotencyKey(KEY))
                    .thenReturn(payment("PAYREUSE00000000002", CALLER, OTHER_ORDER_ID,
                            PaymentChannel.MOCK, PaymentStatus.PENDING));

            BizException exception = assertRejected(() -> paymentService.createPayment(CALLER, KEY,
                    request(ORDER_ID, PaymentChannel.MOCK)));

            assertEquals(ErrorCode.PARAM_INVALID, exception.getCode());
            verifyNoInteractions(orderClient);
        }

        @Test
        @DisplayName("同一幂等键换了支付渠道：拒绝，不返回另一渠道的支付单")
        void rejectsIdempotencyKeyReusedForDifferentChannel() {
            when(paymentMapper.selectByIdempotencyKey(KEY))
                    .thenReturn(payment("PAYREUSE00000000003", CALLER, ORDER_ID,
                            PaymentChannel.WECHAT, PaymentStatus.PENDING));

            BizException exception = assertRejected(() -> paymentService.createPayment(CALLER, KEY,
                    request(ORDER_ID, PaymentChannel.MOCK)));

            assertEquals(ErrorCode.PARAM_INVALID, exception.getCode());
            verifyNoInteractions(orderClient);
        }

        @Test
        @DisplayName("同一调用方同一订单同一渠道同一幂等键：正常复用，不查订单、不落库")
        void reusesPaymentForSameBuyerOrderChannelAndKey() {
            Payment existing = payment("PAYREUSE00000000004", CALLER, ORDER_ID,
                    PaymentChannel.MOCK, PaymentStatus.PENDING);
            when(paymentMapper.selectByIdempotencyKey(KEY)).thenReturn(existing);

            CreatePaymentResponse response = paymentService.createPayment(CALLER, KEY,
                    request(ORDER_ID, PaymentChannel.MOCK));

            assertEquals("PAYREUSE00000000004", response.getPaymentId());
            verifyNoInteractions(orderClient);
            verify(paymentMapper, never()).insert(any(Payment.class));
        }

        @Test
        @DisplayName("存量支付单缺少买家信息：拒绝复用")
        void rejectsExistingPaymentWithoutBuyer() {
            when(paymentMapper.selectByIdempotencyKey(KEY))
                    .thenReturn(payment("PAYREUSE00000000005", null, ORDER_ID,
                            PaymentChannel.MOCK, PaymentStatus.PENDING));

            BizException exception = assertRejected(
                    () -> paymentService.createPayment(CALLER, KEY, request(ORDER_ID, PaymentChannel.MOCK)));

            assertEquals(ErrorCode.FORBIDDEN, exception.getCode());
            verifyNoInteractions(orderClient);
        }
    }

    // ---------- PaymentService：订单已有可用支付单（latest）----------

    @Nested
    @DisplayName("PaymentService 订单已有支付单复用")
    class ByOrderId {

        @Test
        @DisplayName("订单上挂的是别人的待支付单：拒绝，不把他人支付单返回给调用方")
        void rejectsLatestPaymentOwnedByAnotherBuyer() {
            when(paymentMapper.selectByIdempotencyKey(KEY)).thenReturn(null);
            when(paymentMapper.selectLatestByOrderId(ORDER_ID))
                    .thenReturn(payment("PAYREUSE00000000011", OTHER_BUYER, ORDER_ID,
                            PaymentChannel.MOCK, PaymentStatus.PENDING));

            BizException exception = assertRejected(
                    () -> paymentService.createPayment(CALLER, KEY, request(ORDER_ID, PaymentChannel.MOCK)));

            assertEquals(ErrorCode.FORBIDDEN, exception.getCode());
            verifyNoInteractions(orderClient);
        }

        @Test
        @DisplayName("订单已有支付单但渠道与请求不同：拒绝")
        void rejectsLatestPaymentWithDifferentChannel() {
            when(paymentMapper.selectByIdempotencyKey(KEY)).thenReturn(null);
            when(paymentMapper.selectLatestByOrderId(ORDER_ID))
                    .thenReturn(payment("PAYREUSE00000000012", CALLER, ORDER_ID,
                            PaymentChannel.WECHAT, PaymentStatus.PENDING));

            BizException exception = assertRejected(() -> paymentService.createPayment(CALLER, KEY,
                    request(ORDER_ID, PaymentChannel.MOCK)));

            assertEquals(ErrorCode.PARAM_INVALID, exception.getCode());
            verifyNoInteractions(orderClient);
        }

        @Test
        @DisplayName("同一调用方同一订单同一渠道换幂等键：正常复用（既有行为）")
        void reusesPendingPaymentForSameBuyerWithDifferentKey() {
            when(paymentMapper.selectByIdempotencyKey(OTHER_KEY)).thenReturn(null);
            when(paymentMapper.selectLatestByOrderId(ORDER_ID))
                    .thenReturn(payment("PAYREUSE00000000013", CALLER, ORDER_ID,
                            PaymentChannel.MOCK, PaymentStatus.PENDING));

            CreatePaymentResponse response = paymentService.createPayment(CALLER, OTHER_KEY,
                    request(ORDER_ID, PaymentChannel.MOCK));

            assertEquals("PAYREUSE00000000013", response.getPaymentId());
            verifyNoInteractions(orderClient);
            verify(paymentMapper, never()).insert(any(Payment.class));
        }

        @Test
        @DisplayName("订单已支付后换幂等键再来：正常复用已成功支付单")
        void reusesSuccessfulPaymentForSameBuyer() {
            when(paymentMapper.selectByIdempotencyKey(OTHER_KEY)).thenReturn(null);
            when(paymentMapper.selectLatestByOrderId(ORDER_ID))
                    .thenReturn(payment("PAYREUSE00000000014", CALLER, ORDER_ID,
                            PaymentChannel.MOCK, PaymentStatus.SUCCESS));

            CreatePaymentResponse response = paymentService.createPayment(CALLER, OTHER_KEY,
                    request(ORDER_ID, PaymentChannel.MOCK));

            assertEquals("PAYREUSE00000000014", response.getPaymentId());
            verifyNoInteractions(orderClient);
        }

        @Test
        @DisplayName("没有可复用支付单时仍走新建路径（订单校验照旧）")
        void createsNewPaymentWhenNothingToReuse() {
            when(paymentMapper.selectByIdempotencyKey(KEY)).thenReturn(null);
            when(paymentMapper.selectLatestByOrderId(ORDER_ID)).thenReturn(null);

            BizException exception = assertRejected(
                    () -> paymentService.createPayment(CALLER, KEY, request(ORDER_ID, PaymentChannel.MOCK)));

            // orderClient 未打桩返回 null，既有实现应报服务不可用，证明确实进入了新建路径
            assertEquals(ErrorCode.SERVICE_UNAVAILABLE, exception.getCode());
            verify(orderClient).getOrder(ORDER_ID, CALLER);
        }
    }

    // ---------- PaymentTxService：唯一键冲突后的三条回读分支 ----------

    @Nested
    @DisplayName("PaymentTxService 唯一键冲突回读")
    class TxConflictReadBack {

        @Test
        @DisplayName("回读①幂等键命中别人的支付单：不复用，不再回读其他候选")
        void rejectsIdempotencyKeyBranchOwnedByAnotherBuyer() {
            Payment attempt = attempt();
            when(paymentMapper.insert(attempt)).thenThrow(new DuplicateKeyException("uk_idempotency_key"));
            when(paymentMapper.selectByIdempotencyKey(KEY))
                    .thenReturn(payment("PAYREUSE00000000021", OTHER_BUYER, ORDER_ID,
                            PaymentChannel.MOCK, PaymentStatus.PENDING));

            BizException exception = assertRejected(() -> paymentTxService.insertIfAbsent(attempt));

            assertEquals(ErrorCode.FORBIDDEN, exception.getCode());
            verify(paymentMapper, never()).selectPendingByOrderId(any());
        }

        @Test
        @DisplayName("回读①幂等键命中但订单不同：不复用，不再回读其他候选")
        void rejectsIdempotencyKeyBranchWithDifferentOrder() {
            Payment attempt = attempt();
            when(paymentMapper.insert(attempt)).thenThrow(new DuplicateKeyException("uk_idempotency_key"));
            when(paymentMapper.selectByIdempotencyKey(KEY))
                    .thenReturn(payment("PAYREUSE00000000022", CALLER, OTHER_ORDER_ID,
                            PaymentChannel.MOCK, PaymentStatus.PENDING));

            BizException exception = assertRejected(() -> paymentTxService.insertIfAbsent(attempt));

            assertEquals(ErrorCode.PARAM_INVALID, exception.getCode());
            verify(paymentMapper, never()).selectPendingByOrderId(any());
        }

        @Test
        @DisplayName("回读①幂等键命中但渠道不同：不复用，不再回读其他候选")
        void rejectsIdempotencyKeyBranchWithDifferentChannel() {
            Payment attempt = attempt();
            when(paymentMapper.insert(attempt)).thenThrow(new DuplicateKeyException("uk_idempotency_key"));
            when(paymentMapper.selectByIdempotencyKey(KEY))
                    .thenReturn(payment("PAYREUSE00000000023", CALLER, ORDER_ID,
                            PaymentChannel.WECHAT, PaymentStatus.PENDING));

            BizException exception = assertRejected(() -> paymentTxService.insertIfAbsent(attempt));

            assertEquals(ErrorCode.PARAM_INVALID, exception.getCode());
            verify(paymentMapper, never()).selectPendingByOrderId(any());
        }

        @Test
        @DisplayName("回读①命中归属不明（无买家）的支付单：不复用")
        void rejectsIdempotencyKeyBranchWithoutBuyer() {
            Payment attempt = attempt();
            when(paymentMapper.insert(attempt)).thenThrow(new DuplicateKeyException("uk_idempotency_key"));
            when(paymentMapper.selectByIdempotencyKey(KEY))
                    .thenReturn(payment("PAYREUSE00000000031", null, ORDER_ID,
                            PaymentChannel.MOCK, PaymentStatus.PENDING));

            BizException exception = assertRejected(() -> paymentTxService.insertIfAbsent(attempt));

            assertEquals(ErrorCode.FORBIDDEN, exception.getCode());
            verify(paymentMapper, never()).selectPendingByOrderId(any());
        }

        @Test
        @DisplayName("回读②同订单待支付单属于别的买家：不复用，不再回读最新单")
        void rejectsPendingBranchOwnedByAnotherBuyer() {
            Payment attempt = attempt();
            when(paymentMapper.insert(attempt)).thenThrow(new DuplicateKeyException("uk_order_status"));
            when(paymentMapper.selectByIdempotencyKey(KEY)).thenReturn(null);
            when(paymentMapper.selectPendingByOrderId(ORDER_ID))
                    .thenReturn(payment("PAYREUSE00000000024", OTHER_BUYER, ORDER_ID,
                            PaymentChannel.MOCK, PaymentStatus.PENDING));

            BizException exception = assertRejected(() -> paymentTxService.insertIfAbsent(attempt));

            assertEquals(ErrorCode.FORBIDDEN, exception.getCode());
            verify(paymentMapper, never()).selectLatestByOrderId(any());
        }

        @Test
        @DisplayName("回读②同订单待支付单渠道不同：不复用，不再回读最新单")
        void rejectsPendingBranchWithDifferentChannel() {
            Payment attempt = attempt();
            when(paymentMapper.insert(attempt)).thenThrow(new DuplicateKeyException("uk_order_status"));
            when(paymentMapper.selectByIdempotencyKey(KEY)).thenReturn(null);
            when(paymentMapper.selectPendingByOrderId(ORDER_ID))
                    .thenReturn(payment("PAYREUSE00000000025", CALLER, ORDER_ID,
                            PaymentChannel.WECHAT, PaymentStatus.PENDING));

            BizException exception = assertRejected(() -> paymentTxService.insertIfAbsent(attempt));

            assertEquals(ErrorCode.PARAM_INVALID, exception.getCode());
            verify(paymentMapper, never()).selectLatestByOrderId(any());
        }

        @Test
        @DisplayName("回读③同订单最新单属于别的买家：不复用")
        void rejectsLatestSuccessBranchOwnedByAnotherBuyer() {
            Payment attempt = attempt();
            when(paymentMapper.insert(attempt)).thenThrow(new DuplicateKeyException("uk_order_status"));
            when(paymentMapper.selectByIdempotencyKey(KEY)).thenReturn(null);
            when(paymentMapper.selectPendingByOrderId(ORDER_ID)).thenReturn(null);
            when(paymentMapper.selectLatestByOrderId(ORDER_ID))
                    .thenReturn(payment("PAYREUSE00000000026", OTHER_BUYER, ORDER_ID,
                            PaymentChannel.MOCK, PaymentStatus.SUCCESS));

            BizException exception = assertRejected(() -> paymentTxService.insertIfAbsent(attempt));

            assertEquals(ErrorCode.FORBIDDEN, exception.getCode());
        }

        @Test
        @DisplayName("回读③同订单最新单渠道不同：不复用")
        void rejectsLatestRefundedBranchWithDifferentChannel() {
            Payment attempt = attempt();
            when(paymentMapper.insert(attempt)).thenThrow(new DuplicateKeyException("uk_order_status"));
            when(paymentMapper.selectByIdempotencyKey(KEY)).thenReturn(null);
            when(paymentMapper.selectPendingByOrderId(ORDER_ID)).thenReturn(null);
            when(paymentMapper.selectLatestByOrderId(ORDER_ID))
                    .thenReturn(payment("PAYREUSE00000000027", CALLER, ORDER_ID,
                            PaymentChannel.WECHAT, PaymentStatus.REFUNDED));

            BizException exception = assertRejected(() -> paymentTxService.insertIfAbsent(attempt));

            assertEquals(ErrorCode.PARAM_INVALID, exception.getCode());
        }

        @Test
        @DisplayName("回读①同键同单同渠道：正常返回已有支付单（并发重试）")
        void reusesIdempotencyKeyBranchForSameRequest() {
            Payment attempt = attempt();
            Payment existing = payment("PAYREUSE00000000028", CALLER, ORDER_ID,
                    PaymentChannel.MOCK, PaymentStatus.PENDING);
            when(paymentMapper.insert(attempt)).thenThrow(new DuplicateKeyException("uk_idempotency_key"));
            when(paymentMapper.selectByIdempotencyKey(KEY)).thenReturn(existing);

            assertSame(existing, paymentTxService.insertIfAbsent(attempt));
            verify(paymentMapper, never()).selectPendingByOrderId(any());
        }

        @Test
        @DisplayName("回读②同单同渠道待支付单：正常返回已有支付单（并发换键）")
        void reusesPendingBranchForSameBuyer() {
            Payment attempt = attempt();
            Payment existing = payment("PAYREUSE00000000029", CALLER, ORDER_ID,
                    PaymentChannel.MOCK, PaymentStatus.PENDING);
            when(paymentMapper.insert(attempt)).thenThrow(new DuplicateKeyException("uk_order_status"));
            when(paymentMapper.selectByIdempotencyKey(KEY)).thenReturn(null);
            when(paymentMapper.selectPendingByOrderId(ORDER_ID)).thenReturn(existing);

            assertSame(existing, paymentTxService.insertIfAbsent(attempt));
        }

        @Test
        @DisplayName("回读③同单已成功支付单：正常返回已有支付单")
        void reusesLatestSuccessBranchForSameBuyer() {
            Payment attempt = attempt();
            Payment existing = payment("PAYREUSE00000000030", CALLER, ORDER_ID,
                    PaymentChannel.MOCK, PaymentStatus.SUCCESS);
            when(paymentMapper.insert(attempt)).thenThrow(new DuplicateKeyException("uk_order_status"));
            when(paymentMapper.selectByIdempotencyKey(KEY)).thenReturn(null);
            when(paymentMapper.selectPendingByOrderId(ORDER_ID)).thenReturn(null);
            when(paymentMapper.selectLatestByOrderId(ORDER_ID)).thenReturn(existing);

            assertSame(existing, paymentTxService.insertIfAbsent(attempt));
        }

        @Test
        @DisplayName("三条回读都不匹配：不吞掉原始唯一键冲突")
        void rethrowsDuplicateKeyWhenNothingMatches() {
            Payment attempt = attempt();
            when(paymentMapper.insert(attempt)).thenThrow(new DuplicateKeyException("uk_order_status"));
            when(paymentMapper.selectByIdempotencyKey(KEY)).thenReturn(null);
            when(paymentMapper.selectPendingByOrderId(ORDER_ID)).thenReturn(null);
            when(paymentMapper.selectLatestByOrderId(ORDER_ID)).thenReturn(null);

            assertThrows(DuplicateKeyException.class, () -> paymentTxService.insertIfAbsent(attempt));
        }
    }

    // ---------- 工具方法 ----------

    /**
     * 统一断言：复用被拒绝，且以 BizException 抛出、错误码不是成功。
     *
     * <p>只约束「拒绝」这一共同语义；具体码字由各用例按
     * 越权 {@link ErrorCode#FORBIDDEN} / 订单与渠道错配 {@link ErrorCode#PARAM_INVALID} 断言。
     */
    private BizException assertRejected(org.junit.jupiter.api.function.Executable call) {
        BizException exception = assertThrows(BizException.class, call);
        assertTrue(exception.getCode() != ErrorCode.SUCCESS, "错误码不应是成功");
        return exception;
    }

    private CreatePaymentRequest request(String orderId, PaymentChannel channel) {
        CreatePaymentRequest request = new CreatePaymentRequest();
        request.setOrderId(orderId);
        request.setChannel(channel);
        return request;
    }

    private Payment payment(String paymentId, String buyerId, String orderId, PaymentChannel channel,
                           PaymentStatus status) {
        Payment payment = new Payment();
        payment.setPaymentId(paymentId);
        payment.setPayNo("P" + paymentId);
        payment.setOrderId(orderId);
        payment.setBuyerId(buyerId);
        payment.setSellerId("S10001");
        payment.setChannel(channel);
        payment.setAmount(AMOUNT);
        payment.setRefundedAmount(0L);
        payment.setStatus(status);
        payment.setIsDeleted(false);
        return payment;
    }

    private Payment attempt() {
        Payment payment = payment("PAYATTEMPT0000000001", CALLER, ORDER_ID,
                PaymentChannel.MOCK, PaymentStatus.PENDING);
        payment.setIdempotencyKey(KEY);
        return payment;
    }
}
