package com.mall.payment.entity;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 佣金费率规则（#144 第二块）。费率用万分比整数，回退顺序：店铺 > 类目 > 平台。
 */
@Data
public class CommissionRule {

    public static final String SCOPE_PLATFORM = "PLATFORM";
    public static final String SCOPE_CATEGORY = "CATEGORY";
    public static final String SCOPE_SHOP = "SHOP";
    /** 平台级规则的固定目标 */
    public static final String TARGET_PLATFORM = "*";
    /** 万分比基数 */
    public static final long RATE_BASE = 10000L;

    private String ruleId;
    private String scope;
    private String targetId;
    private Integer rate;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private String createdBy;
    private String updatedBy;
}
