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
import com.mall.payment.mapper.RefundMapper;
import com.mall.payment.mapper.SettlementLedgerMapper;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.transaction.annotation.Isolation;
import java.util.TreeMap;
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
    private final RefundMapper refundMapper;
    private final SettlementLedgerMapper ledger;
    private static final java.time.ZoneId SETTLEMENT_ZONE = java.time.ZoneId.of("Asia/Shanghai");

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public SettlementGenerateResponse generate(String periodRaw, String operatorId) {
        YearMonth period = parsePeriod(periodRaw);
        LocalDateTime start = period.atDay(1).atStartOfDay();
        LocalDateTime end = period.plusMonths(1).atDay(1).atStartOfDay();

        return generateForPeriod(period, start, end, operatorId, null);
    }

    private SettlementGenerateResponse generateForPeriod(YearMonth period, LocalDateTime start, LocalDateTime end,
                                                          String operatorId, String onlySeller) {
        List<OrderCompletion> candidates = orderCompletionMapper.selectCompletedInPeriod(start, end);
        Map<String, Integer> shopRates = new HashMap<>();
        Map<String, Integer> categoryRates = new HashMap<>();
        Integer platformRate = null;
        for (CommissionRule rule : commissionRuleMapper.selectAll()) {
            if (CommissionRule.SCOPE_SHOP.equals(rule.getScope())) {
                shopRates.put(rule.getTargetId(), rule.getRate());
            } else if (CommissionRule.SCOPE_CATEGORY.equals(rule.getScope())) {
                categoryRates.put(rule.getTargetId(), rule.getRate());
            } else if (CommissionRule.SCOPE_PLATFORM.equals(rule.getScope())
                    && CommissionRule.TARGET_PLATFORM.equals(rule.getTargetId())) {
                platformRate = rule.getRate();
            }
        }
        int defaultRate = platformRate == null ? 0 : platformRate;

        Map<String, List<OrderCompletion>> bySeller = new TreeMap<>();
        for (String sellerId : settlementMapper.selectPendingSellers(start)) {
            bySeller.put(sellerId, new ArrayList<>());
        }
        for (String sellerId : ledger.selectSellersWithAdjustments(start)) {
            bySeller.computeIfAbsent(sellerId, key -> new ArrayList<>());
        }
        for (OrderCompletion order : candidates) {
            bySeller.computeIfAbsent(order.getSellerId(), key -> new ArrayList<>()).add(order);
        }
        if (onlySeller != null) {
            bySeller.keySet().removeIf(seller -> !onlySeller.equals(seller));
        }

        int created = 0;
        int updated = 0;
        int skipped = 0;
        for (Map.Entry<String, List<OrderCompletion>> entry : bySeller.entrySet()) {
            String sellerId = entry.getKey();
            List<OrderCompletion> orders = entry.getValue();
            Integer shopRate = shopRates.get(sellerId);
            lockSeller(sellerId);
            recordPaidRefunds(sellerId);
            // Upsert first, then lock the current row; a snapshot read may miss a concurrent insert.
            Settlement fresh = new Settlement();
            fresh.setSettlementId(IdGenerator.settlementId());
            fresh.setSellerId(sellerId);
            fresh.setPeriodStart(start);
            fresh.setPeriodEnd(end);
            fresh.setGrossAmount(0L);
            fresh.setCommissionAmount(0L);
            fresh.setNetAmount(0L);
            fresh.setCreatedBy(operatorId);
            fresh.setUpdatedBy(operatorId);
            settlementMapper.insertIgnoreDuplicate(fresh);
            Settlement settlement = settlementMapper.lockBySellerPeriod(sellerId, start);
            if (settlement == null || !!Settlement.STATUS_PAID.equals(settlement.getStatus())) {
                skipped++;
                continue;
            }

            String settlementId = settlement.getSettlementId();
            insertItems(settlementId, orders, shopRate, categoryRates, defaultRate);
            rebuildEditableStatements(sellerId, operatorId);
            Settlement current = settlementMapper.selectById(settlementId);
            if (fresh.getSettlementId().equals(settlementId)) {
                created++;
            } else if (!current.getGrossAmount().equals(settlement.getGrossAmount())
                    || !current.getCommissionAmount().equals(settlement.getCommissionAmount())
                    || !current.getNetAmount().equals(settlement.getNetAmount())) {
                updated++;
            }
        }
        log.info("生成结算单, operatorId={}, period={}, candidates={}, created={}, updated={}, skipped={}",
                operatorId, period, candidates.size(), created, updated, skipped);
        return SettlementGenerateResponse.of(period.toString(), created, updated, skipped,
                candidates.size());
    }

    public List<CommissionRule> commissionRules() {
        return commissionRuleMapper.selectAll();
    }

    public void saveCommissionRule(String scope, String targetId, Integer rate, String operatorId) {
        if (scope == null || !List.of(CommissionRule.SCOPE_PLATFORM, CommissionRule.SCOPE_CATEGORY,
                CommissionRule.SCOPE_SHOP).contains(scope) || rate == null || rate < 0 || rate > 10000) {
            throw new BizException(ErrorCode.PARAM_INVALID, "费率范围应为 0–10000 万分比，作用范围须明确");
        }
        String target = targetId == null ? "" : targetId.trim();
        if (CommissionRule.SCOPE_PLATFORM.equals(scope) ? !"*".equals(target)
                : target.isEmpty() || target.length() > 32 || "*".equals(target)) {
            throw new BizException(ErrorCode.PARAM_INVALID, "平台目标为 *，店铺和类目规则须填写对应 ID");
        }
        CommissionRule rule = new CommissionRule();
        rule.setRuleId(IdGenerator.settlementId());
        rule.setScope(scope);
        rule.setTargetId(target);
        rule.setRate(rate);
        rule.setCreatedBy(operatorId);
        rule.setUpdatedBy(operatorId);
        commissionRuleMapper.upsert(rule);
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

    @Transactional(isolation = Isolation.READ_COMMITTED, noRollbackFor = SettlementAmountChanged.class)
    public SettlementResponse markPaid(String settlementId, String targetStatus, String operatorId,
                                       Long expectedNet, String reference, String remark) {
        if (!Settlement.STATUS_PAID.equals(targetStatus)) {
            throw new BizException(ErrorCode.PARAM_INVALID, "状态接口当前仅支持标记为 PAID");
        }
        Settlement initial = settlementMapper.selectById(settlementId);
        if (initial == null) {
            throw new BizException(ErrorCode.SETTLEMENT_NOT_FOUND, "结算单不存在");
        }
        lockSeller(initial.getSellerId());
        Settlement current = settlementMapper.lockBySellerPeriod(initial.getSellerId(), initial.getPeriodStart());
        LocalDateTime now = LocalDateTime.now(SETTLEMENT_ZONE);
        if (current == null || Settlement.STATUS_PAID.equals(current.getStatus()) || now.isBefore(current.getPeriodEnd())) {
            throw new BizException(ErrorCode.SETTLEMENT_STATE_INVALID, "结算单已打款、周期未结束或状态不允许打款");
        }
        String receipt = reference == null ? "" : reference.strip();
        String note = remark == null || remark.isBlank() ? null : remark.strip();
        if (expectedNet == null || expectedNet < 0 || receipt.isEmpty() || receipt.length() > 100
                || (note != null && note.length() > 255)) {
            throw new BizException(ErrorCode.PARAM_INVALID, "请确认应结金额并填写有效且唯一的打款凭证编号");
        }
        generateForPeriod(YearMonth.from(current.getPeriodStart()), current.getPeriodStart(),
                current.getPeriodEnd(), operatorId, current.getSellerId());
        Settlement recalculated = settlementMapper.selectById(settlementId);
        if (!expectedNet.equals(recalculated.getNetAmount())) {
            throw new SettlementAmountChanged();
        }
        if (settlementMapper.markPaid(settlementId, operatorId) != 1) {
            throw new BizException(ErrorCode.SETTLEMENT_STATE_INVALID, "结算单已打款或状态不允许打款");
        }
        try {
            ledger.insertPayout(settlementId, new SettlementLedgerMapper.Payout(receipt, operatorId, expectedNet, now, note));
        } catch (DuplicateKeyException e) {
            throw new BizException(ErrorCode.PARAM_INVALID, "打款凭证编号已经使用");
        }
        return toResponse(settlementMapper.selectById(settlementId));
    }

    private static final class SettlementAmountChanged extends BizException {
        SettlementAmountChanged() {
            super(ErrorCode.SETTLEMENT_AMOUNT_CHANGED, "应结金额已变化，请刷新账单后重新确认");
        }
    }

    private void lockSeller(String sellerId) {
        ledger.ensureSeller(sellerId);
        ledger.lockSeller(sellerId);
    }

    private void recordPaidRefunds(String sellerId) {
        for (var item : ledger.selectPaidRefundChanges(sellerId)) {
            long original = Math.addExact(item.grossAmount(), item.refundedAmount());
            long refunded = Math.min(original, item.totalRefunded());
            if (refunded <= Math.max(item.refundedAmount(), item.recordedRefunded())) continue;
            long remaining = original - refunded;
            long commission = recalculatedCommission(remaining, item.commissionSnapshot(),
                    item.grossAmount(), item.commissionAmount());
            long deduction = Math.max(0L, (item.grossAmount() - item.commissionAmount())
                    - (remaining - commission) - item.recordedDeduction());
            ledger.insertAdjustment(sellerId, item, refunded, deduction);
        }
    }

    private void rebuildEditableStatements(String sellerId, String operatorId) {
        List<Settlement> statements = settlementMapper.lockEditableBySeller(sellerId);
        // Reallocate only editable statements, earliest first. Uses belonging to paid statements stay immutable.
        statements.forEach(statement -> ledger.clearApplied(statement.getSettlementId()));
        for (Settlement statement : statements) {
            String id = statement.getSettlementId();
            List<SettlementItem> items = refreshItems(id);
            long gross = items.stream().mapToLong(SettlementItem::getGrossAmount).reduce(0L, Math::addExact);
            long commission = items.stream().mapToLong(SettlementItem::getCommissionAmount).reduce(0L, Math::addExact);
            long net = gross - commission;
            if (net > 0) {
                for (var adjustment : ledger.lockAvailableAdjustments(sellerId, statement.getPeriodStart())) {
                    long used = Math.min(net, adjustment.amount() - adjustment.appliedAmount());
                    if (used <= 0) continue;
                    ledger.applyAdjustment(id, adjustment.id(), used);
                    net -= used;
                    if (net == 0) break;
                }
            }
            settlementMapper.setAmounts(id, gross, commission, net, operatorId);
        }
    }

    private static long recalculatedCommission(long remaining, String snapshot, long gross, long commission) {
        return snapshot == null ? SettlementCommission.historicalCommission(remaining, gross, commission)
                : SettlementCommission.calculate(remaining, snapshot);
    }

    private List<SettlementItem> refreshItems(String settlementId) {
        List<SettlementItem> items = settlementItemMapper.lockBySettlementId(settlementId);
        for (SettlementItem item : items) {
            long originalGross = Math.addExact(item.getGrossAmount(), item.getRefundedAmount());
            long refunded = Math.min(originalGross, refundMapper.sumSettlementRefunds(item.getOrderId()));
            if (refunded <= item.getRefundedAmount()) {
                continue;
            }
            long remaining = originalGross - refunded;
            String snapshot = item.getCommissionSnapshot();
            long commission = snapshot == null
                    ? SettlementCommission.historicalCommission(remaining, item.getGrossAmount(), item.getCommissionAmount())
                    : SettlementCommission.calculate(remaining, snapshot);
            item.setGrossAmount(remaining);
            item.setCommissionAmount(commission);
            item.setRefundedAmount(refunded);
            settlementItemMapper.updateRefundedAmounts(item);
        }
        return items;
    }

    private void insertItems(String settlementId, List<OrderCompletion> orders, Integer shopRate,
                             Map<String, Integer> categoryRates, int defaultRate) {
        for (OrderCompletion order : orders) {
            var lines = shopRate == null ? orderCompletionMapper.selectLines(order.getOrderId()).stream()
                    .map(line -> new SettlementCommission.Line(line.subtotal(),
                            categoryRates.getOrDefault(line.categoryId(), defaultRate)))
                    .toList() : List.of(new SettlementCommission.Line(1, shopRate));
            if (lines.isEmpty()) {
                lines = List.of(new SettlementCommission.Line(1, defaultRate));
            }
            String snapshot = SettlementCommission.snapshot(lines);
            SettlementItem item = new SettlementItem();
            item.setSettlementId(settlementId);
            item.setOrderId(order.getOrderId());
            long refunded = Math.min(order.getPayAmount(), order.getSuccessfulRefundAmount());
            long remaining = order.getPayAmount() - refunded;
            if (remaining <= 0) continue;
            item.setGrossAmount(remaining);
            item.setRefundedAmount(refunded);
            item.setCommissionSnapshot(snapshot);
            item.setCommissionAmount(SettlementCommission.calculate(remaining, snapshot));
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
            String id = settlement.getSettlementId();
            var response = SettlementResponse.of(settlement, itemsBySettlement.getOrDefault(id, List.of()));
            response.setPayable(!Settlement.STATUS_PAID.equals(settlement.getStatus())
                    && !LocalDateTime.now(SETTLEMENT_ZONE).isBefore(settlement.getPeriodEnd()));
            var payout = ledger.selectPayout(id);
            if (payout != null) {
                response.setPayout(new SettlementResponse.Payout(payout.reference(), payout.operatorId(),
                        payout.amount(), payout.paidAt(), payout.remark()));
            }
            response.setAdjustments(ledger.selectApplied(id).stream().map(a ->
                    new SettlementResponse.Adjustment(a.sourceSettlementId(), a.orderId(), a.amount())).toList());
            responses.add(response);
        }
        return responses;
    }

    private SettlementResponse toResponse(Settlement settlement) {
        return toResponses(List.of(settlement)).get(0);
    }

    private YearMonth parsePeriod(String periodRaw) {
        String period = periodRaw == null || periodRaw.isBlank()
                ? YearMonth.now(SETTLEMENT_ZONE).toString()
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
