package com.mall.order.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/** 平台关键操作审计记录（任务 #58）。 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuditLog {

    private Long id;
    private String auditId;
    private String operatorId;
    private String operatorRole;
    private String action;
    private String targetType;
    private String targetId;
    private String detail;
    private String result;
    private LocalDateTime createdAt;
}
