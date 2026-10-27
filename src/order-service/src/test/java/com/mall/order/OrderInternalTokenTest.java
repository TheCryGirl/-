package com.mall.order;

import com.mall.order.common.BizException;
import com.mall.order.common.ErrorCode;
import com.mall.order.config.InternalTokenVerifier;
import com.mall.order.controller.OrderController;
import com.mall.order.service.OrderService;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class OrderInternalTokenTest {

    private static final String EXPECTED = "payment-order-secret";

    @Test
    void configuredTokenRejectsMissingAndWrongTokensForPaidAndRefunded() {
        InternalTokenVerifier verifier = verifier(EXPECTED);
        OrderController controller = new OrderController(mock(OrderService.class), verifier);

        assertForbidden(() -> controller.paid("O1", null));
        assertForbidden(() -> controller.paid("O1", "wrong"));
        assertForbidden(() -> controller.refunded("O1", null));
        assertForbidden(() -> controller.refunded("O1", "wrong"));
    }

    @Test
    void configuredTokenAllowsPaymentAndRefundNotifications() {
        OrderService orderService = mock(OrderService.class);
        OrderController controller = new OrderController(orderService, verifier(EXPECTED));

        assertThatCode(() -> controller.paid("O1", EXPECTED)).doesNotThrowAnyException();
        assertThatCode(() -> controller.refunded("O1", EXPECTED)).doesNotThrowAnyException();

        verify(orderService).markPaid("O1");
        verify(orderService).markRefunded("O1");
    }

    @Test
    void emptyConfiguredTokenKeepsDevelopmentCallsOpen() {
        OrderService orderService = mock(OrderService.class);
        OrderController controller = new OrderController(orderService, verifier(""));

        assertThatCode(() -> controller.paid("O1", null)).doesNotThrowAnyException();
        assertThatCode(() -> controller.refunded("O1", "any-token")).doesNotThrowAnyException();
    }

    private static InternalTokenVerifier verifier(String expectedToken) {
        InternalTokenVerifier verifier = new InternalTokenVerifier();
        ReflectionTestUtils.setField(verifier, "expectedToken", expectedToken);
        return verifier;
    }

    private static void assertForbidden(Runnable action) {
        assertThatThrownBy(() -> action.run())
                .isInstanceOf(BizException.class)
                .extracting(e -> ((BizException) e).getCode())
                .isEqualTo(ErrorCode.FORBIDDEN);
    }
}

