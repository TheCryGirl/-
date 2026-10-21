package com.mall.user.util;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

/**
 * 密码工具。使用 BCrypt 自动加盐哈希，避免明文存储。
 */
public final class PasswordUtil {

    private static final BCryptPasswordEncoder ENCODER = new BCryptPasswordEncoder();

    private PasswordUtil() {
    }

    /**
     * 对明文密码进行加盐哈希。
     */
    public static String hash(String rawPassword) {
        return ENCODER.encode(rawPassword);
    }

    /**
     * 校验明文密码是否与哈希值匹配。
     */
    public static boolean matches(String rawPassword, String encodedPassword) {
        return ENCODER.matches(rawPassword, encodedPassword);
    }
}
