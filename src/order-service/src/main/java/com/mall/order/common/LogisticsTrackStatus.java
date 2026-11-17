package com.mall.order.common;

/**
 * 物流轨迹状态（任务 #138），与 issue 约定的五个取值一一对应。
 *
 * <p>{@code abnormal} 只影响展示：异常件在时间线上标红，但不改变订单状态。
 * 轨迹状态与订单状态是两条线——例如出现 SIGNED 也不会把订单改成 COMPLETED，
 * 收货仍由定时任务到期或买家手动确认。
 */
public enum LogisticsTrackStatus {

    COLLECTED("已揽收", false),
    IN_TRANSIT("运输中", false),
    OUT_FOR_DELIVERY("派送中", false),
    SIGNED("已签收", false),
    EXCEPTION("异常件", true);

    private final String label;
    private final boolean abnormal;

    LogisticsTrackStatus(String label, boolean abnormal) {
        this.label = label;
        this.abnormal = abnormal;
    }

    public String getLabel() {
        return label;
    }

    public boolean isAbnormal() {
        return abnormal;
    }

    /** 按编码解析，忽略大小写与首尾空白；不认识返回 null，由调用方转 10001。 */
    public static LogisticsTrackStatus parse(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String value = raw.trim().toUpperCase();
        for (LogisticsTrackStatus status : values()) {
            if (status.name().equals(value)) {
                return status;
            }
        }
        return null;
    }
}
