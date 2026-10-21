package com.mall.cart.common;

import java.security.SecureRandom;

/**
 * 购物车业务 ID 生成器。ID 带随机部分，避免被枚举。
 */
public final class IdGenerator {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final char[] HEX = "0123456789abcdef".toCharArray();

    private IdGenerator() {
    }

    public static String cartId() {
        return "C" + randomHex(20);
    }

    public static String cartItemId() {
        return "I" + randomHex(20);
    }

    private static String randomHex(int length) {
        char[] result = new char[length];
        for (int i = 0; i < length; i++) {
            result[i] = HEX[RANDOM.nextInt(HEX.length)];
        }
        return new String(result);
    }
}
