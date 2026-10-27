package com.mall.order.entity;

import lombok.Data;

/** 平台配置变更记录（任务 #43） */
@Data
public class ConfigAudit {

    private Long id;
    private String operatorId;
    private String dataId;
    private String configKey;
    private String oldValue;
    private String newValue;
    private String createdAt;
}
