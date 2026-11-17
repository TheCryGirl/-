package com.mall.user.entity;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 运费模板（#136），与 freight_template 表一一对应。一个店铺一个模板。
 */
@Data
public class FreightTemplate {

    private String templateId;
    private String shopId;
    private String name;
    /** 包邮门槛，单位分；0 表示不包邮 */
    private Long freeThreshold;
    /** ACTIVE / DISABLED */
    private String status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private String createdBy;
    private String updatedBy;
    private Integer isDeleted;
}
