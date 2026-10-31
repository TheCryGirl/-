package com.mall.product.common;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * 业务 ID 生成器。主键使用字符串且有随机部分，避免被枚举。
 */
public final class IdGenerator {

    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final char[] HEX = "0123456789abcdef".toCharArray();

    private IdGenerator() {
    }

    public static String productId() {
        return "P" + randomDigits(10);
    }

    public static String skuId() {
        return "S" + randomDigits(10);
    }

    /**
     * 预占单 ID：时间戳 + 16 位十六进制随机后缀。
     *
     * <p>早期实现是「R + 10 位纯数字」，仅 10^10 个取值。在 500 线程并发预占时
     * 按生日碰撞计算有约 1% 概率生成重复 ID，表现为 stock_reservation 主键或
     * uk_order_id 冲突，Issue48InventoryConcurrencyTest 因此间歇性失败。
     * 返回长度 1+14+16=31，不超过 reservation_id VARCHAR(32) 的容量。
     */
    public static String reservationId() {
        return "R" + LocalDateTime.now().format(TIME_FORMATTER) + randomHex(16);
    }

    public static String orderNo() {
        return LocalDateTime.now().format(TIME_FORMATTER) + randomDigits(6);
    }

    private static String randomDigits(int count) {
        StringBuilder builder = new StringBuilder(count);
        for (int i = 0; i < count; i++) {
            builder.append(RANDOM.nextInt(10));
        }
        return builder.toString();
    }

    public static String randomHex(int length) {
        char[] result = new char[length];
        for (int i = 0; i < length; i++) {
            result[i] = HEX[RANDOM.nextInt(HEX.length)];
        }
        return new String(result);
    }
}
