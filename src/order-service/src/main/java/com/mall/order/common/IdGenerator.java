package com.mall.order.common;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public final class IdGenerator {

    private static final DateTimeFormatter ORDER_NO_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
    private static final SecureRandom RANDOM = new SecureRandom();

    private IdGenerator() {
    }

    public static String orderId() {
        return "O" + randomDigits(10);
    }

    /**
     * 日期 + 6 位随机数，满足“全局唯一且不可枚举”的订单号要求。
     */
    public static String orderNo() {
        return LocalDateTime.now().format(ORDER_NO_FORMATTER) + randomDigits(6);
    }

    public static String randomDigits(int count) {
        StringBuilder builder = new StringBuilder(count);
        for (int i = 0; i < count; i++) {
            builder.append(RANDOM.nextInt(10));
        }
        return builder.toString();
    }
}
