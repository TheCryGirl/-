package com.mall.order.dto.response;

import lombok.Data;

import java.util.List;

/**
 * 订单物流信息（任务 #138）。
 *
 * <p>未发货订单也正常返回 200：{@code shipped=false}、{@code tracks} 为空数组、
 * {@code message} 给出「尚未发货」这类明确提示，不返回错误码。
 * 这样前端只需判断 shipped 与 tracks 是否为空，不必区分「未发货」和「查不到」。
 */
@Data
public class OrderLogisticsResponse {

    private String orderId;
    private boolean shipped;
    private String expressCompany;
    private String expressCompanyLabel;
    private String trackingNo;
    private String shippedAt;

    /**
     * 剩余可确认分钟数，向上取整；已到期为 0，未发货或已完成为 null。
     * 前端据此显示「预计 X 天后自动确认收货」，不足一天时自动显示小时/分钟。
     */
    private Long autoConfirmRemainingMinutes;

    /** 自动确认收货的阈值（分钟），来自配置，便于前端文案与实际规则一致 */
    private long autoConfirmMinutes;

    /** 轨迹按发生时间正序，最新一条在末尾 */
    private List<LogisticsTrackResponse> tracks;

    /** 无轨迹或未发货时的明确提示；有轨迹时为 null */
    private String message;
}
