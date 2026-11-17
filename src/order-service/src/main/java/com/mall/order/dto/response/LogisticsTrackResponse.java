package com.mall.order.dto.response;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 物流轨迹节点（任务 #138）。abnormal 由后端算好，前端不重复维护一份枚举映射。 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class LogisticsTrackResponse {

    private String trackId;
    private String status;
    private String statusLabel;
    private String description;
    private String occurredAt;
    /** 异常件在前端标红；SIGNED 等正常节点为 false */
    private boolean abnormal;
}
