package com.mall.product.common;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public final class TimeUtils {

    public static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private TimeUtils() {
    }

    public static String format(LocalDateTime time) {
        return time == null ? null : time.format(FORMATTER);
    }

    public static LocalDateTime parse(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return LocalDateTime.parse(value.trim(), FORMATTER);
    }
}
