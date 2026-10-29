package com.mall.user.common;

import java.security.SecureRandom;

/**
 * 业务 ID 生成器。ID 用字符串且有随机部分，避免被枚举。
 */
public final class IdGenerator {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final char[] HEX = "0123456789abcdef".toCharArray();

    private IdGenerator() {
    }

    public static String userId() {
        return "U" + randomHex(24);
    }

    public static String addressId() {
        return "A" + randomHex(24);
    }

    public static String shopId() {
        return "SH" + randomHex(22);
    }

    public static String favoriteId() {
        return "F" + randomHex(24);
    }

    public static String historyId() {
        return "H" + randomHex(24);
    }

    private static String randomHex(int length) {
        char[] result = new char[length];
        for (int i = 0; i < length; i++) {
            result[i] = HEX[RANDOM.nextInt(HEX.length)];
        }
        return new String(result);
    }
}
