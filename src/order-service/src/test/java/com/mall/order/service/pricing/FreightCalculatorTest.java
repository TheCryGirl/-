package com.mall.order.service.pricing;

import com.mall.order.client.dto.FreightConfigDTO;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 运费计算验收（#136）：包邮门槛、分地区、多档与未配置模板。
 */
class FreightCalculatorTest {

    @Test
    void freeThresholdMakesOrderFreeButLowerAmountIsCharged() {
        FreightConfigDTO config = config(9900L, List.of(rule("DEFAULT", 0L, 600L)));

        assertThat(FreightCalculator.calc(10000L, config, "天津市")).isZero();
        assertThat(FreightCalculator.calc(9900L, config, "天津市")).isZero();
        assertThat(FreightCalculator.calc(5000L, config, "天津市")).isEqualTo(600L);
    }

    @Test
    void regionRuleWinsOverDefaultAndUnknownRegionFallsBack() {
        FreightConfigDTO config = config(0L, List.of(
                rule("DEFAULT", 0L, 600L),
                rule("河北省", 0L, 800L)));

        assertThat(FreightCalculator.calc(3000L, config, "河北省")).isEqualTo(800L);
        assertThat(FreightCalculator.calc(3000L, config, "天津市")).isEqualTo(600L);
        // 没有配过的省份走 DEFAULT
        assertThat(FreightCalculator.calc(3000L, config, "广东省")).isEqualTo(600L);
        // 没有地址（地区为空）也只走 DEFAULT
        assertThat(FreightCalculator.calc(3000L, config, null)).isEqualTo(600L);
    }

    @Test
    void multiTierRulePicksHighestMatchedTier() {
        FreightConfigDTO config = config(0L, List.of(
                rule("河北省", 0L, 800L),
                rule("河北省", 5000L, 300L),
                rule("河北省", 10000L, 0L)));

        assertThat(FreightCalculator.calc(2000L, config, "河北省")).isEqualTo(800L);
        assertThat(FreightCalculator.calc(5000L, config, "河北省")).isEqualTo(300L);
        assertThat(FreightCalculator.calc(12000L, config, "河北省")).isZero();
    }

    @Test
    void amountBelowEveryTierUsesLowestTierInsteadOfFreeShipping() {
        FreightConfigDTO config = config(0L, List.of(
                rule("河北省", 5000L, 300L),
                rule("河北省", 10000L, 0L)));

        assertThat(FreightCalculator.calc(1000L, config, "河北省")).isEqualTo(300L);
    }

    @Test
    void missingTemplateOrEmptyRulesMeansZeroFreight() {
        assertThat(FreightCalculator.calc(5000L, null, "天津市")).isZero();
        assertThat(FreightCalculator.calc(5000L, config(0L, List.of()), "天津市")).isZero();
        // 模板存在但只有别的省份规则：没有 DEFAULT 也不收运费
        FreightConfigDTO onlyHebei = config(0L, List.of(rule("河北省", 0L, 800L)));
        assertThat(FreightCalculator.calc(5000L, onlyHebei, "天津市")).isZero();
    }

    @Test
    void negativeFeeIsClampedToZero() {
        FreightConfigDTO config = config(0L, List.of(rule("DEFAULT", 0L, -100L)));
        assertThat(FreightCalculator.calc(5000L, config, "天津市")).isZero();
    }

    private static FreightConfigDTO config(Long freeThreshold, List<FreightConfigDTO.FreightRuleDTO> rules) {
        FreightConfigDTO config = new FreightConfigDTO();
        config.setTemplateId("FT-TEST");
        config.setSellerId("U-SELLER");
        config.setFreeThreshold(freeThreshold);
        config.setRules(new ArrayList<>(rules));
        return config;
    }

    private static FreightConfigDTO.FreightRuleDTO rule(String region, long minAmount, long fee) {
        FreightConfigDTO.FreightRuleDTO rule = new FreightConfigDTO.FreightRuleDTO();
        rule.setRegionCode(region);
        rule.setMinAmount(minAmount);
        rule.setFee(fee);
        return rule;
    }
}
