package com.mall.order.dto.request;

import lombok.Data;

/** 各服务写入操作审计的请求体（任务 #58）。 */
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
