package com.mall.user.entity;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 分地区运费规则（#136）。
 *
 * <p>{@code regionCode} 为省级名称（如「河北省」），{@code DEFAULT} 表示其它地区兜底；
 * {@code minAmount} 为该条规则适用的最低商品金额（分），同一地区可配多档。
 */
@Data
public class FreightRule {

    private String ruleId;
    private String templateId;
    private String regionCode;
    private Long minAmount;
    private Long fee;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private Integer isDeleted;
}
