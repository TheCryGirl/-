package com.mall.order.entity;

import com.mall.order.common.LogisticsTrackStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 物流轨迹（任务 #138），一行一条节点。
 *
 * <p>本期不接第三方快递 API，轨迹由卖家或管理员手工录入，
 * {@code rawJson} 保留录入时的原始报文，便于以后接外部接口时直接落库对账。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderLogisticsTrack {

    private Long id;
    private String trackId;
    private String orderId;
    private LogisticsTrackStatus status;
    private String description;
    private LocalDateTime occurredAt;
    private String rawJson;
    private LocalDateTime createdAt;
    private String createdBy;
}
