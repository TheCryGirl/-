package com.mall.order.service.pricing;

import com.mall.order.client.dto.FreightConfigDTO;

import java.util.List;
import java.util.Locale;

/**
 * 运费计算（#136）：纯函数，便于单测覆盖「包邮门槛 / 分地区 / 多档 / 未配置」四种情况。
 *
 * <p>规则：
 * <ol>
 *   <li>模板为空或没有规则 → 运费 0（未配置模板的店铺不收运费，不报错）；</li>
 *   <li>{@code freeThreshold>0} 且商品小计 ≥ 门槛 → 运费 0（包邮）；</li>
 *   <li>先按收货省份匹配 regionCode，匹配不到回退 {@code DEFAULT}；两者都没有 → 0；</li>
 *   <li>同一地区多档时取「min_amount ≤ 小计」中门槛最高的一档；都高于小计则取门槛最低的一档；</li>
 *   <li>金额一律为「分」，不做除法，因此不存在二次四舍五入。</li>
 * </ol>
 */
public final class FreightCalculator {

    public static final String DEFAULT_REGION = "DEFAULT";

    private FreightCalculator() {
    }

    public static long calc(long goodsAmount, FreightConfigDTO config, String region) {
        if (config == null || config.getRules() == null || config.getRules().isEmpty()) {
            return 0L;
        }
        long freeThreshold = config.getFreeThreshold() == null ? 0L : config.getFreeThreshold();
        if (freeThreshold > 0 && goodsAmount >= freeThreshold) {
            return 0L;
        }
        List<FreightConfigDTO.FreightRuleDTO> candidates = pickRegion(config.getRules(), region);
        if (candidates.isEmpty()) {
            return 0L;
        }
        // 按 min_amount 升序：取「小计达到的最高档」，都不满足时退回最低档作为该地区的基础运费
        List<FreightConfigDTO.FreightRuleDTO> sorted = candidates.stream()
                .sorted((left, right) -> Long.compare(minAmount(left), minAmount(right)))
                .toList();
        FreightConfigDTO.FreightRuleDTO chosen = sorted.get(0);
        for (FreightConfigDTO.FreightRuleDTO rule : sorted) {
            if (minAmount(rule) <= goodsAmount) {
                chosen = rule;
            } else {
                break;
            }
        }
        return chosen.getFee() == null ? 0L : Math.max(0L, chosen.getFee());
    }

    /** 先精确匹配省份，再回退 DEFAULT；匹配不区分大小写。 */
    static List<FreightConfigDTO.FreightRuleDTO> pickRegion(List<FreightConfigDTO.FreightRuleDTO> rules,
                                                            String region) {
        String normalized = region == null ? "" : region.trim();
        List<FreightConfigDTO.FreightRuleDTO> exact = rules.stream()
                .filter(rule -> rule.getRegionCode() != null
                        && !rule.getRegionCode().isBlank()
                        && rule.getRegionCode().trim().equalsIgnoreCase(normalized))
                .toList();
        if (!exact.isEmpty()) {
            return exact;
        }
        return rules.stream()
                .filter(rule -> rule.getRegionCode() != null
                        && DEFAULT_REGION.equals(rule.getRegionCode().trim().toUpperCase(Locale.ROOT)))
                .toList();
    }

    private static long minAmount(FreightConfigDTO.FreightRuleDTO rule) {
        return rule.getMinAmount() == null ? 0L : rule.getMinAmount();
    }
}
