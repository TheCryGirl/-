package com.mall.user.dto.response;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 店铺运费配置出参（#136）。
 *
 * <p>卖家运费设置页与 order-service 的内部调用共用：{@code freeThreshold=0} 表示不包邮；
 * 没有配置过模板时返回 {@code templateId=null}、{@code freeThreshold=0}、空规则表，
 * 下单时按「未配置模板的店铺运费为 0」处理。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class FreightConfigResponse {

    private String templateId;
    private String shopId;
    private String sellerId;
    private String name;
    private Long freeThreshold;
    private String status;
    private List<FreightRuleResponse> rules;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class FreightRuleResponse {
        private String regionCode;
        private Long minAmount;
        private Long fee;
    }
}
