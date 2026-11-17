package com.mall.user.service;

import com.mall.user.common.BizException;
import com.mall.user.common.ErrorCode;
import com.mall.user.common.IdGenerator;
import com.mall.user.dto.request.FreightConfigRequest;
import com.mall.user.dto.request.FreightRuleRequest;
import com.mall.user.dto.response.FreightConfigResponse;
import com.mall.user.dto.response.ShopResponse;
import com.mall.user.entity.FreightRule;
import com.mall.user.entity.FreightTemplate;
import com.mall.user.mapper.FreightMapper;
import com.mall.user.mapper.ShopMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * 运费模板配置（#136）。
 *
 * <p>配置侧放在 user-service（跟着店铺走），计算侧在 order-service：
 * order-service 通过内部接口 {@code GET /internal/shops/{sellerId}/freight} 取模板，
 * 再按收货地区与商品小计算每家店的运费。
 *
 * <p>约定：{@code free_threshold=0} 表示不包邮；没有模板的店铺运费一律按 0 处理，不报错。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FreightService {

    /** 兜底地区码：未匹配到具体省级规则时使用 */
    public static final String DEFAULT_REGION = "DEFAULT";
    private static final String STATUS_ACTIVE = "ACTIVE";

    private final FreightMapper freightMapper;
    private final ShopMapper shopMapper;
    private final ShopService shopService;

    /** 卖家读取自己店铺的运费配置；没配置过时返回空模板，前端据此展示「未设置」 */
    public FreightConfigResponse getMine(String sellerId) {
        ShopResponse shop = shopService.getMine(sellerId);
        return load(shop.getShopId(), shop.getSellerId());
    }

    /** 卖家整体保存运费配置：先落模板，再整体覆盖规则表，最后把模板挂到店铺上 */
    @Transactional
    public FreightConfigResponse saveMine(String sellerId, FreightConfigRequest request) {
        ShopResponse shop = shopService.getMine(sellerId);
        List<FreightRuleRequest> rules = normalizeRules(request.getRules());

        FreightTemplate template = freightMapper.selectTemplateByShopId(shop.getShopId());
        LocalDateTime now = LocalDateTime.now();
        if (template == null) {
            template = new FreightTemplate();
            template.setTemplateId(IdGenerator.freightTemplateId());
            template.setShopId(shop.getShopId());
            template.setName(request.getName().trim());
            template.setFreeThreshold(request.getFreeThreshold());
            template.setStatus(STATUS_ACTIVE);
            template.setCreatedAt(now);
            template.setUpdatedAt(now);
            template.setCreatedBy(sellerId);
            template.setUpdatedBy(sellerId);
            freightMapper.insertTemplate(template);
        } else {
            template.setName(request.getName().trim());
            template.setFreeThreshold(request.getFreeThreshold());
            template.setStatus(STATUS_ACTIVE);
            template.setUpdatedAt(now);
            template.setUpdatedBy(sellerId);
            freightMapper.updateTemplate(template);
            freightMapper.softDeleteRulesByTemplateId(template.getTemplateId());
        }

        for (FreightRuleRequest rule : rules) {
            FreightRule entity = new FreightRule();
            entity.setRuleId(IdGenerator.freightRuleId());
            entity.setTemplateId(template.getTemplateId());
            entity.setRegionCode(rule.getRegionCode());
            entity.setMinAmount(rule.getMinAmount());
            entity.setFee(rule.getFee());
            entity.setCreatedAt(now);
            entity.setUpdatedAt(now);
            freightMapper.insertRule(entity);
        }
        shopMapper.updateFreightTemplateId(shop.getShopId(), template.getTemplateId(), now);
        log.info("卖家保存运费配置, sellerId={}, shopId={}, templateId={}, rules={}",
                sellerId, shop.getShopId(), template.getTemplateId(), rules.size());
        return load(shop.getShopId(), shop.getSellerId());
    }

    /** 内部接口：按卖家取模板，供 order-service 计算运费 */
    public FreightConfigResponse getBySellerId(String sellerId) {
        if (sellerId == null || sellerId.isBlank()) {
            throw new BizException(ErrorCode.PARAM_INVALID, "卖家ID不能为空");
        }
        var shop = shopMapper.selectBySellerId(sellerId.trim());
        if (shop == null) {
            // 没有店铺记录的卖家按「未配置模板」处理，运费为 0
            FreightConfigResponse empty = new FreightConfigResponse();
            empty.setSellerId(sellerId.trim());
            empty.setFreeThreshold(0L);
            empty.setRules(List.of());
            return empty;
        }
        return load(shop.getShopId(), shop.getSellerId());
    }

    private FreightConfigResponse load(String shopId, String sellerId) {
        FreightConfigResponse response = new FreightConfigResponse();
        response.setShopId(shopId);
        response.setSellerId(sellerId);
        response.setFreeThreshold(0L);
        response.setRules(List.of());
        FreightTemplate template = freightMapper.selectTemplateByShopId(shopId);
        if (template == null) {
            return response;
        }
        response.setTemplateId(template.getTemplateId());
        response.setName(template.getName());
        response.setFreeThreshold(template.getFreeThreshold() == null ? 0L : template.getFreeThreshold());
        response.setStatus(template.getStatus());
        List<FreightConfigResponse.FreightRuleResponse> rules = new ArrayList<>();
        for (FreightRule rule : freightMapper.selectRulesByTemplateId(template.getTemplateId())) {
            rules.add(new FreightConfigResponse.FreightRuleResponse(
                    rule.getRegionCode(), rule.getMinAmount(), rule.getFee()));
        }
        response.setRules(rules);
        return response;
    }

    /** 校验并规整规则：地区统一去空白，DEFAULT 统一大写，同地区同门槛不允许重复 */
    private List<FreightRuleRequest> normalizeRules(List<FreightRuleRequest> raw) {
        if (raw == null || raw.isEmpty()) {
            return List.of();
        }
        if (raw.size() > 50) {
            throw new BizException(ErrorCode.PARAM_INVALID, "运费规则最多 50 条");
        }
        Set<String> seen = new HashSet<>();
        List<FreightRuleRequest> rules = new ArrayList<>(raw.size());
        for (FreightRuleRequest rule : raw) {
            String region = rule.getRegionCode() == null ? "" : rule.getRegionCode().trim();
            if (region.isEmpty()) {
                throw new BizException(ErrorCode.PARAM_INVALID, "地区不能为空");
            }
            String normalizedRegion = DEFAULT_REGION.equalsIgnoreCase(region)
                    ? DEFAULT_REGION
                    : region.toUpperCase(Locale.ROOT).equals(DEFAULT_REGION) ? DEFAULT_REGION : region;
            long minAmount = rule.getMinAmount() == null ? -1 : rule.getMinAmount();
            long fee = rule.getFee() == null ? -1 : rule.getFee();
            if (minAmount < 0) {
                throw new BizException(ErrorCode.PARAM_INVALID, "最低金额不能为负数");
            }
            if (fee < 0) {
                throw new BizException(ErrorCode.PARAM_INVALID, "运费不能为负数");
            }
            if (!seen.add(normalizedRegion + "|" + minAmount)) {
                throw new BizException(ErrorCode.PARAM_INVALID, "同一地区的最低金额不能重复: " + normalizedRegion);
            }
            FreightRuleRequest normalized = new FreightRuleRequest();
            normalized.setRegionCode(normalizedRegion);
            normalized.setMinAmount(minAmount);
            normalized.setFee(fee);
            rules.add(normalized);
        }
        return rules;
    }
}
