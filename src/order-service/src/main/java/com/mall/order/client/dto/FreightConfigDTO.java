package com.mall.order.client.dto;

import lombok.Data;

import java.util.List;

/**
 * 卖家运费模板（#136）：order-service 通过内部接口从 user-service 取。
 *
 * <p>{@code freeThreshold=0} 表示不包邮；{@code rules} 为空表示该店不收运费。
 */
@Data
public class FreightConfigDTO {

    private String templateId;
    private String shopId;
    private String sellerId;
    private String name;
    /** 包邮门槛，单位分；0 表示不包邮 */
    private Long freeThreshold;
    private String status;
    private List<FreightRuleDTO> rules;

    @Data
    public static class FreightRuleDTO {
        /** 省级名称（如「河北省」）或 DEFAULT 兜底 */
        private String regionCode;
        /** 适用该规则的最低商品金额，单位分 */
        private Long minAmount;
        /** 运费，单位分 */
        private Long fee;
    }
}
