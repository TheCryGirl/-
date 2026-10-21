package com.mall.order;

import com.mall.order.common.OrderStateMachine;
import com.mall.order.common.OrderStatus;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OrderStateMachineTest {

    @Test
    void onlyDocumentedTransitionsAreAllowed() {
        assertThat(OrderStateMachine.canTransit(OrderStatus.PENDING_PAYMENT, OrderStatus.PAID)).isTrue();
        assertThat(OrderStateMachine.canTransit(OrderStatus.PENDING_PAYMENT, OrderStatus.CANCELLED)).isTrue();
        assertThat(OrderStateMachine.canTransit(OrderStatus.PAID, OrderStatus.SHIPPED)).isTrue();
        assertThat(OrderStateMachine.canTransit(OrderStatus.SHIPPED, OrderStatus.COMPLETED)).isTrue();
        assertThat(OrderStateMachine.canTransit(OrderStatus.COMPLETED, OrderStatus.REFUNDED)).isTrue();

        assertThat(OrderStateMachine.canTransit(OrderStatus.PAID, OrderStatus.CANCELLED)).isFalse();
        assertThat(OrderStateMachine.canTransit(OrderStatus.CANCELLED, OrderStatus.PAID)).isFalse();
        assertThat(OrderStateMachine.canTransit(OrderStatus.REFUNDED, OrderStatus.PAID)).isFalse();
    }

    @Test
    void illegalTransitionThrowsBizException() {
        assertThatThrownBy(() -> OrderStateMachine.assertCanTransit(OrderStatus.CANCELLED, OrderStatus.PAID))
                .isInstanceOf(com.mall.order.common.BizException.class);
    }
}
