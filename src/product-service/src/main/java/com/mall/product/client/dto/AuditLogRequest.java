package com.mall.product.client.dto;

import lombok.Data;

/** 上报操作审计日志的请求体（任务 #58），对应 order-service /internal/audit-logs。 */
@Data
public class AuditLogRequest {

    private String operatorId;
    private String operatorRole;
    private String action;
    private String targetType;
    private String targetId;
    private String detail;
    private String result;
}
