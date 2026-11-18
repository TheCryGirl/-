package com.mall.payment.service;

import com.mall.payment.common.BizException;
import com.mall.payment.common.ErrorCode;
import com.mall.payment.common.IdGenerator;
import com.mall.payment.common.PageResult;
import com.mall.payment.dto.internal.OrderCompletion;
import com.mall.payment.dto.response.SettlementGenerateResponse;
import com.mall.payment.dto.response.SettlementResponse;
import com.mall.payment.entity.CommissionRule;
import com.mall.payment.entity.Settlement;
import com.mall.payment.entity.SettlementItem;
import com.mall.payment.mapper.CommissionRuleMapper;
import com.mall.payment.mapper.OrderCompletionMapper;
import com.mall.payment.mapper.SettlementItemMapper;
import com.mall.payment.mapper.SettlementMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 佣金与结算（#144 第二块）。
 *
 * <p>口径：只结算 order-service 中 status='COMPLETED' 的子订单（完成时间取
 * updated_at，#138 确认收货时写入）；已存在成功退款的订单直接剔除；
 * 佣金 = 订单实付 × 费率，费率万分比整数，每笔按分四舍五入一次；
 * 费率回退：店铺（SHOP，按卖家ID）> 平台（PLATFORM）；类目级规则表结构已预留，
 * 因子订单不带类目快照、product-service 无类目查询内部接口，本期不参与计算。
 * 无任何规则命中时费率为 0（不收佣金）。
 *
 * <p>防重复：settlement 的 (seller_id, period_start) 唯一键 + settlement_item 的
 * order_id 唯一键；重复生成同一周期时，PENDING 结算单走条件 UPDATE 追加新订单，
 * CONFIRMED/PAID 结算单冻结不再追加。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SettlementService {

    private final SettlementMapper settlementMapper;
    private final SettlementItemMapper settlementItemMapper;
    private final CommissionRuleMapper commissionRuleMapper;
    private final OrderCompletionMapper orderCompletionMapper;

    @Transactional
    public SettlementGenerateResponse generate(String periodRaw, String operatorId) {
        YearMonth period = parsePeriod(periodRaw);
        LocalDateTime start = period.atDay(1).atStartOfDay();
        LocalDateTime end = period.plusMonths(1).atDay(1).atStartOfDay();

        List<OrderCompletion> candidates = orderCompletionMapper.selectCompletedInPeriod(start, end);
        Map<String, Integer> shopRates = new HashMap<>();
        Integer platformRate = null;
        for (CommissionRule rule : commissionRuleMapper.selectAll()) {
            if (CommissionRule.SCOPE_SHOP.equals(rule.getScope())) {
                shopRates.put(rule.getTargetId(), rule.getRate());
            } else if (CommissionRule.SCOPE_PLATFORM.equals(rule.getScope())
                    && CommissionRule.TARGET_PLATFORM.equals(rule.getTargetId())) {
                platformRate = rule.getRate();
            }
        }
        int defaultRate = platformRate == null ? 0 : platformRate;

        Map<String, List<OrderCompletion>> bySeller = new LinkedHashMap<>();
        for (OrderCompletion order : candidates) {
            bySeller.computeIfAbsent(order.getSellerId(), key -> new ArrayList<>()).add(order);
        }

        int created = 0;
        int updated = 0;
        int skipped = 0;
        for (Map.Entry<String, List<OrderCompletion>> entry : bySeller.entrySet()) {
            String sellerId = entry.getKey();
            List<OrderCompletion> orders = entry.getValue();
            int rate = shopRates.getOrDefault(sellerId, defaultRate);
            long gross = 0L;
            long commission = 0L;
            for (OrderCompletion order : orders) {
                gross += order.getPayAmount();
                commission += commissionOf(order.getPayAmount(), rate);
            }
            long net = gross - commission;

            Settlement settlement = settlementMapper.selectBySellerPeriod(sellerId, start);
            if (settlement == null) {
                Settlement fresh = new Settlement();
                fresh.setSettlementId(IdGenerator.settlementId());
                fresh.setSellerId(sellerId);
                fresh.setPeriodStart(start);
                fresh.setPeriodEnd(end);
                fresh.setGrossAmount(gross);
                fresh.setCommissionAmount(commission);
                fresh.setNetAmount(net);
                fresh.setStatus(Settlement.STATUS_PENDING);
                fresh.setCreatedBy(operatorId);
                fresh.setUpdatedBy(operatorId);
                settlementMapper.insertIgnoreDuplicate(fresh);
                // 并发下可能由另一次生成先落库，重查拿真实行，避免明细挂到不存在的结算单上
                settlement = settlementMapper.selectBySellerPeriod(sellerId, start);
                insertItems(settlement.getSettlementId(), orders, rate);
                created++;
                continue;
            }
            if (Settlement.STATUS_PENDING.equals(settlement.getStatus())) {
                int affected = settlementMapper.addAmounts(
                        settlement.getSettlementId(), gross, commission, net, operatorId);
                if (affected == 1) {
                    insertItems(settlement.getSettlementId(), orders, rate);
                    updated++;
                } else {
                    skipped++;
                }
            } else {
                skipped++;
            }
        }
        log.info("生成结算单, operatorId={}, period={}, candidates={}, created={}, updated={}, skipped={}",
                operatorId, period, candidates.size(), created, updated, skipped);
        return SettlementGenerateResponse.of(period.toString(), created, updated, skipped,
                candidates.size());
    }

    /** 管理员视角：可按卖家与周期过滤 */
    public PageResult<SettlementResponse> list(String sellerId, String periodRaw, int page, int size) {
        int safePage = Math.max(page, 1);
        int safeSize = Math.min(Math.max(size, 1), 100);
        String seller = sellerId == null || sellerId.isBlank() ? null : sellerId.trim();
        LocalDateTime periodStart = periodOf(periodRaw);
        long total = settlementMapper.count(seller, periodStart);
        List<Settlement> rows = settlementMapper.selectPage(
                seller, periodStart, (safePage - 1) * safeSize, safeSize);
        return PageResult.of(toResponses(rows), total, safePage, safeSize);
    }

    /** 卖家视角：只能看自己的结算单 */
    public PageResult<SettlementResponse> listForSeller(String sellerId, String periodRaw,
                                                        int page, int size) {
        return list(sellerId, periodRaw, page, size);
    }

    @Transactional
    public SettlementResponse markPaid(String settlementId, String targetStatus, String operatorId) {
        if (!Settlement.STATUS_PAID.equals(targetStatus)) {
            throw new BizException(ErrorCode.PARAM_INVALID, "状态接口当前仅支持标记为 PAID");
        }
        if (settlementMapper.selectById(settlementId) == null) {
            throw new BizException(ErrorCode.SETTLEMENT_NOT_FOUND, "结算单不存在");
        }
        int affected = settlementMapper.markPaid(settlementId, operatorId);
        if (affected == 0) {
            throw new BizException(ErrorCode.SETTLEMENT_STATE_INVALID, "结算单已打款或状态不允许打款");
        }
        return toResponse(settlementMapper.selectById(settlementId));
    }

    /** 每笔订单按分四舍五入一次：pay × rate / 10000 */
    private long commissionOf(long payAmount, int rate) {
        return Math.round(payAmount * (double) rate / CommissionRule.RATE_BASE);
    }

    private void insertItems(String settlementId, List<OrderCompletion> orders, int rate) {
        for (OrderCompletion order : orders) {
            SettlementItem item = new SettlementItem();
            item.setSettlementId(settlementId);
            item.setOrderId(order.getOrderId());
            item.setGrossAmount(order.getPayAmount());
            item.setCommissionAmount(commissionOf(order.getPayAmount(), rate));
            settlementItemMapper.insertIgnoreDuplicate(item);
        }
    }

    private List<SettlementResponse> toResponses(List<Settlement> rows) {
        if (rows.isEmpty()) {
            return List.of();
        }
        List<String> ids = rows.stream().map(Settlement::getSettlementId).toList();
        Map<String, List<SettlementItem>> itemsBySettlement = new HashMap<>();
        for (SettlementItem item : settlementItemMapper.selectBySettlementIds(ids)) {
            itemsBySettlement.computeIfAbsent(item.getSettlementId(), key -> new ArrayList<>()).add(item);
        }
        List<SettlementResponse> responses = new ArrayList<>(rows.size());
        for (Settlement settlement : rows) {
            responses.add(SettlementResponse.of(settlement,
                    itemsBySettlement.getOrDefault(settlement.getSettlementId(), List.of())));
        }
        return responses;
    }

    private SettlementResponse toResponse(Settlement settlement) {
        return toResponses(List.of(settlement)).get(0);
    }

    private YearMonth parsePeriod(String periodRaw) {
        String period = periodRaw == null || periodRaw.isBlank()
                ? YearMonth.now().toString()
                : periodRaw.trim();
        if (!period.matches("\\d{4}-(0[1-9]|1[0-2])")) {
            throw new BizException(ErrorCode.PARAM_INVALID, "周期格式应为 yyyy-MM");
        }
        return YearMonth.parse(period);
    }

    private LocalDateTime periodOf(String periodRaw) {
        return periodRaw == null || periodRaw.isBlank()
                ? null
                : parsePeriod(periodRaw).atDay(1).atStartOfDay();
    }
}
