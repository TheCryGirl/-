package com.mall.order.common;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * 订单状态机：
 * PENDING_PAYMENT -> PAID -> SHIPPED -> COMPLETED -> REFUNDED
 * PENDING_PAYMENT -> CANCELLED
 */
public final class OrderStateMachine {

    private static final Map<OrderStatus, Set<OrderStatus>> ALLOWED = new EnumMap<>(OrderStatus.class);

    static {
        ALLOWED.put(OrderStatus.PENDING_PAYMENT, EnumSet.of(OrderStatus.PAID, OrderStatus.CANCELLED));
        ALLOWED.put(OrderStatus.PAID, EnumSet.of(OrderStatus.SHIPPED));
        ALLOWED.put(OrderStatus.SHIPPED, EnumSet.of(OrderStatus.COMPLETED));
        ALLOWED.put(OrderStatus.COMPLETED, EnumSet.of(OrderStatus.REFUNDED));
        ALLOWED.put(OrderStatus.CANCELLED, EnumSet.noneOf(OrderStatus.class));
        ALLOWED.put(OrderStatus.REFUNDED, EnumSet.noneOf(OrderStatus.class));
    }

    private OrderStateMachine() {
    }

    public static boolean canTransit(OrderStatus from, OrderStatus to) {
        return from != null && to != null && ALLOWED.getOrDefault(from, Set.of()).contains(to);
    }

    public static void assertCanTransit(OrderStatus from, OrderStatus to) {
        if (!canTransit(from, to)) {
            throw new BizException(ErrorCode.ORDER_STATE_INVALID,
                    "订单状态不能从 " + from + " 迁移到 " + to);
        }
    }
}
