package com.mall.order.common;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public final class IdGenerator {

    private static final DateTimeFormatter ORDER_NO_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final char[] HEX = "0123456789abcdef".toCharArray();

    private IdGenerator() {
    }

    /**
     * 订单 ID：时间戳 + 12 位十六进制随机后缀。
     *
     * <p>早期实现是「O + 10 位纯数字」，仅 10^9 个取值，高并发下单时存在生日碰撞风险，
     * 一旦撞号会体现为订单主键冲突或幂等记录指向错误订单。
     * 返回长度 1+14+12=27，不超过 order_id VARCHAR(32) 的容量。
     */
    public static String orderId() {
        return "O" + LocalDateTime.now().format(ORDER_NO_FORMATTER) + randomHex(12);
    }

    /** 主订单 ID（#135）：M 前缀 + 时间戳 + 12 位十六进制随机后缀，长度 27。 */
    public static String mainOrderId() {
        return "M" + LocalDateTime.now().format(ORDER_NO_FORMATTER) + randomHex(12);
    }

    public static String randomDigits(int count) {
        StringBuilder builder = new StringBuilder(count);
        for (int i = 0; i < count; i++) {
            builder.append(RANDOM.nextInt(10));
        }
        return builder.toString();
    }

    /**
     * 日期 + 6 位随机数，满足“全局唯一且不可枚举”的订单号要求。
     */
    public static String orderNo() {
        return LocalDateTime.now().format(ORDER_NO_FORMATTER) + randomDigits(6);
    }

    /**
     * 物流轨迹 ID：T + 14 位时间 + 8 位十六进制随机，长度 23。
     *
     * <p>与订单号同思路：不可枚举。轨迹是逐条追加的，用「时间前缀 + 随机后缀」
     * 既保留可读的先后关系，也不会因为自增而暴露平台录入了多少条轨迹。
     */
    public static String trackId() {
        return "T" + LocalDateTime.now().format(ORDER_NO_FORMATTER) + randomHex(8);
    }

    public static String randomHex(int length) {
        char[] result = new char[length];
        for (int i = 0; i < length; i++) {
            result[i] = HEX[RANDOM.nextInt(HEX.length)];
        }
        return new String(result);
    }
}
