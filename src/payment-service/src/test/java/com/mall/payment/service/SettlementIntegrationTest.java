package com.mall.payment.service;

import com.mall.payment.common.BizException;
import com.mall.payment.common.ErrorCode;
import com.mall.payment.common.PageResult;
import com.mall.payment.dto.response.SettlementGenerateResponse;
import com.mall.payment.dto.response.SettlementResponse;
import com.mall.payment.entity.Settlement;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 佣金结算集成测试（#144 第二块）。
 *
 * <p>覆盖：3 笔订单金额逐笔可核、四舍五入只发生一次、退款订单剔除、
 * 店铺费率覆盖平台费率、同周期重复生成只追加不重复、PAID 冻结、
 * 无规则费率为 0、周期边界与格式校验、标记打款状态机与卖家数据隔离。
 */
@SpringBootTest
class SettlementIntegrationTest {

    private static final String ADMIN = "ADMIN01";
    private static final String SELLER_A = "S1";
    private static final String SELLER_B = "S2";
    private static final String PERIOD = "2024-11";
    private static final LocalDateTime T1 = LocalDateTime.of(2024, 11, 5, 10, 0, 0);
    private static final LocalDateTime T2 = LocalDateTime.of(2024, 11, 20, 15, 30, 0);
    private static final LocalDateTime MONTH_END = LocalDateTime.of(2024, 11, 30, 23, 59, 59);
    private static final LocalDateTime NEXT_MONTH_START = LocalDateTime.of(2024, 12, 1, 0, 0, 0);

    @Autowired
    private SettlementService settlementService;
    @Autowired
    private JdbcTemplate jdbcTemplate;
    @org.springframework.boot.test.mock.mockito.SpyBean
    private com.mall.payment.mapper.OrderCompletionMapper orderCompletionMapper;

    @BeforeEach
    void cleanDatabase() {
        for (String table : List.of("settlement_adjustment_use", "settlement_adjustment", "settlement_payout", "settlement_seller_lock")) {
            jdbcTemplate.update("DELETE FROM " + table);
        }
        jdbcTemplate.update("DELETE FROM settlement_item");
        jdbcTemplate.update("DELETE FROM settlement");
        jdbcTemplate.update("DELETE FROM commission_rule");
        jdbcTemplate.update("DELETE FROM refund");
        jdbcTemplate.update("DELETE FROM mall_order.order_item");
        jdbcTemplate.update("DELETE FROM mall_order.`order`");
    }

    @Test
    @DisplayName("3 笔订单生成结算单：实付合计-佣金=应结，逐笔可核，四舍五入只一次")
    void generateCreatesSettlementWithVerifiableAmounts() {
        insertRule("R1", "PLATFORM", "*", 300);
        insertOrder("O1", SELLER_A, 10000L, "COMPLETED", T1);
        insertOrder("O2", SELLER_A, 20050L, "COMPLETED", T1);
        insertOrder("O3", SELLER_A, 999L, "COMPLETED", T2);

        SettlementGenerateResponse result = settlementService.generate(PERIOD, ADMIN);

        assertEquals(1, result.getCreated());
        assertEquals(3, result.getOrderCount());
        Settlement settlement = onlySettlement();
        assertEquals(31049L, settlement.getGrossAmount());
        // 10000*3%=300，20050*3%=601.5→602（半分进位），999*3%=29.97→30
        assertEquals(932L, settlement.getCommissionAmount());
        assertEquals(30117L, settlement.getNetAmount());
        assertEquals(Settlement.STATUS_PENDING, settlement.getStatus());

        SettlementResponse view = settlementService.list(SELLER_A, PERIOD, 1, 20).getList().get(0);
        assertEquals(3, view.getItems().size());
        assertTrue(view.getItems().stream().anyMatch(item ->
                item.getOrderId().equals("O2") && item.getGrossAmount() == 20050L
                        && item.getCommissionAmount() == 602L));
    }

    @Test
    @DisplayName("四舍五入只发生一次：999.99 分佣金入成 1000 分而不是先截断再逐笔漂移")
    void roundingHappensExactlyOnce() {
        insertRule("R1", "PLATFORM", "*", 300);
        insertOrder("O1", SELLER_A, 33333L, "COMPLETED", T1);

        settlementService.generate(PERIOD, ADMIN);

        assertEquals(1000L, onlySettlement().getCommissionAmount());
    }

    @Test
    @DisplayName("部分退款按净额计佣，已退款状态不进结算，驳回不扣减")
    void refundedOrdersAreExcluded() {
        insertRule("R1", "PLATFORM", "*", 300);
        insertOrder("O1", SELLER_A, 5000L, "COMPLETED", T1);
        insertRefund("RF1", "O1", "SUCCESS");
        insertOrder("O2", SELLER_A, 6000L, "REFUNDED", T1);
        insertOrder("O3", SELLER_A, 7000L, "COMPLETED", T1);
        insertRefund("RF2", "O3", "REJECTED");

        settlementService.generate(PERIOD, ADMIN);

        Settlement settlement = onlySettlement();
        assertEquals(11900L, settlement.getGrossAmount());
        assertEquals(357L, settlement.getCommissionAmount());
        assertEquals(2, count("settlement_item"));
    }

    @Test
    void pendingStatementRepricesToNetRefundUsingFrozenRateOnEveryRun() {
        insertRule("R1", "PLATFORM", "*", 300);
        insertOrder("O1", SELLER_A, 10000L, "COMPLETED", T1);
        settlementService.generate(PERIOD, ADMIN);
        jdbcTemplate.update("UPDATE commission_rule SET rate=2000 WHERE rule_id='R1'");
        insertRefund("RF1", "O1", "SUCCESS");
        insertRefund("RF2", "O1", "PROCESSING");

        settlementService.generate(PERIOD, ADMIN);
        settlementService.generate(PERIOD, ADMIN);
        assertEquals(9900L, onlySettlement().getGrossAmount());
        assertEquals(297L, onlySettlement().getCommissionAmount());
        assertEquals(9603L, onlySettlement().getNetAmount());
        assertEquals(1, count("settlement_item"));
    }

    @Test
    @DisplayName("店铺费率覆盖平台费率，其他店铺按平台费率")
    void shopRuleOverridesPlatformRule() {
        insertRule("R1", "PLATFORM", "*", 300);
        insertRule("R2", "SHOP", SELLER_A, 500);
        insertOrder("O1", SELLER_A, 10000L, "COMPLETED", T1);
        insertOrder("O2", SELLER_B, 10000L, "COMPLETED", T1);

        settlementService.generate(PERIOD, ADMIN);

        Settlement forA = settlementMapperRow(SELLER_A);
        Settlement forB = settlementMapperRow(SELLER_B);
        assertEquals(500L, forA.getCommissionAmount());
        assertEquals(300L, forB.getCommissionAmount());
        assertEquals(2, count("settlement"));
    }

    @Test
    void categorySnapshotsUseWeightedCommissionAndFallbackRules() {
        insertRule("R1", "PLATFORM", "*", 300);
        insertRule("R2", "CATEGORY", "C1", 500);
        insertRule("R3", "CATEGORY", "C2", 1000);
        insertRule("R4", "SHOP", SELLER_B, 200);
        insertOrder("O-MIX", SELLER_A, 399L, "COMPLETED", T1);
        insertOrder("O-OLD", SELLER_A, 1000L, "COMPLETED", T1);
        insertOrder("O-SHOP", SELLER_B, 1000L, "COMPLETED", T1);
        jdbcTemplate.update("INSERT INTO mall_order.order_item(order_id, category_id_snapshot, subtotal)"
                + " VALUES ('O-MIX', 'C1', 100),('O-MIX', 'C2', 300),('O-SHOP', 'C2', 1000)");

        settlementService.generate(PERIOD, ADMIN);

        assertEquals(35L, jdbcTemplate.queryForObject(
                "SELECT commission_amount FROM settlement_item WHERE order_id='O-MIX'", Long.class));
        assertEquals(65L, settlementMapperRow(SELLER_A).getCommissionAmount());
        assertEquals(20L, settlementMapperRow(SELLER_B).getCommissionAmount());
    }

    @Test
    @DisplayName("同一周期重复生成：不产生重复结算单，PENDING 只追加新订单")
    void regenerateSamePeriodAppendsWithoutDuplicates() {
        insertRule("R1", "PLATFORM", "*", 300);
        insertOrder("O1", SELLER_A, 10000L, "COMPLETED", T1);

        settlementService.generate(PERIOD, ADMIN);
        String settlementId = onlySettlement().getSettlementId();

        insertOrder("O2", SELLER_A, 20000L, "COMPLETED", T2);
        SettlementGenerateResponse again = settlementService.generate(PERIOD, ADMIN);

        assertEquals(0, again.getCreated());
        assertEquals(1, again.getUpdated());
        assertEquals(1, count("settlement"));
        assertEquals(2, count("settlement_item"));
        Settlement merged = onlySettlement();
        assertEquals(settlementId, merged.getSettlementId());
        assertEquals(30000L, merged.getGrossAmount());
        assertEquals(900L, merged.getCommissionAmount());
        assertEquals(29100L, merged.getNetAmount());
    }

    @Test
    void concurrentGenerationCountsEachNewOrderOnlyOnce() throws Exception {
        insertRule("RC", "PLATFORM", "*", 300);
        insertOrder("O1", SELLER_A, 10000L, "COMPLETED", T1);
        settlementService.generate(PERIOD, ADMIN);
        insertOrder("O2", SELLER_A, 20000L, "COMPLETED", T2);

        var scanned = new java.util.concurrent.CyclicBarrier(2);
        var start = T1.withDayOfMonth(1).toLocalDate().atStartOfDay();
        var candidates = orderCompletionMapper.selectCompletedInPeriod(start, start.plusMonths(1));
        assertEquals(List.of("O2"), candidates.stream().map(order -> order.getOrderId()).toList());
        org.mockito.Mockito.doAnswer(call -> {
            scanned.await(10, java.util.concurrent.TimeUnit.SECONDS);
            return candidates;
        }).when(orderCompletionMapper).selectCompletedInPeriod(start, start.plusMonths(1));
        var workers = java.util.concurrent.Executors.newFixedThreadPool(2);
        try {
            var a = workers.submit(() -> settlementService.generate(PERIOD, ADMIN));
            var b = workers.submit(() -> settlementService.generate(PERIOD, ADMIN));
            a.get(20, java.util.concurrent.TimeUnit.SECONDS);
            b.get(20, java.util.concurrent.TimeUnit.SECONDS);
        } finally {
            workers.shutdownNow();
        }
        assertEquals(2, count("settlement_item"));
        assertEquals(30000L, onlySettlement().getGrossAmount());
        assertEquals(900L, onlySettlement().getCommissionAmount());
        assertEquals(29100L, onlySettlement().getNetAmount());
    }

    @Test
    @DisplayName("已打款结算单冻结：重复生成不再追加，也不影响既有金额")
    void paidSettlementIsFrozen() {
        insertRule("R1", "PLATFORM", "*", 300);
        insertOrder("O1", SELLER_A, 10000L, "COMPLETED", T1);
        settlementService.generate(PERIOD, ADMIN);

        String settlementId = onlySettlement().getSettlementId();
        settlementService.markPaid(settlementId, "PAID", ADMIN, onlySettlement().getNetAmount(), "TEST-" + settlementId, null);

        insertOrder("O2", SELLER_A, 20000L, "COMPLETED", T2);
        SettlementGenerateResponse again = settlementService.generate(PERIOD, ADMIN);

        assertEquals(0, again.getCreated());
        assertEquals(0, again.getUpdated());
        assertEquals(1, again.getSkippedFrozen());
        assertEquals(1, count("settlement"));
        assertEquals(1, count("settlement_item"));
        assertEquals(10000L, onlySettlement().getGrossAmount());
        assertEquals(Settlement.STATUS_PAID, onlySettlement().getStatus());
    }

    @Test
    void openMonthCannotBeMarkedPaid() {
        LocalDateTime start = java.time.YearMonth.now(java.time.ZoneId.of("Asia/Shanghai")).atDay(1).atStartOfDay();
        String period = java.time.YearMonth.from(start).toString();
        insertOrder("O-NOW", SELLER_A, 10000L, "COMPLETED", start);
        settlementService.generate(period, ADMIN);
        String id = onlySettlement().getSettlementId();
        assertEquals(ErrorCode.SETTLEMENT_STATE_INVALID, assertThrows(BizException.class,
                () -> settlementService.markPaid(id, "PAID", ADMIN, 10000L, "OPEN-MONTH", null)).getCode());
        assertEquals("PENDING", onlySettlement().getStatus());
    }

    @Test
    void payoutRequiresFreshAmountAndUniqueReference() {
        insertRule("RP", "PLATFORM", "*", 300);
        insertOrder("O-PAID", SELLER_A, 10000L, "COMPLETED", T1);
        settlementService.generate(PERIOD, ADMIN);
        String id = onlySettlement().getSettlementId();
        insertRefund("RF-P", "O-PAID", "SUCCESS");
        assertEquals(ErrorCode.SETTLEMENT_AMOUNT_CHANGED, assertThrows(BizException.class,
                () -> settlementService.markPaid(id, "PAID", ADMIN, 9700L, "REF-42", null)).getCode());
        assertEquals(9603L, onlySettlement().getNetAmount());
        assertEquals(0, count("settlement_payout"));
        var paid = settlementService.markPaid(id, "PAID", ADMIN, 9603L, "REF-42", "手工凭证");
        assertEquals("REF-42", paid.getPayout().reference());
        assertEquals(9603L, paid.getPayout().amount());
        insertOrder("O-OTHER", SELLER_B, 500L, "COMPLETED", T1);
        settlementService.generate(PERIOD, ADMIN);
        var other = settlementMapperRow(SELLER_B);
        assertEquals(ErrorCode.PARAM_INVALID, assertThrows(BizException.class,
                () -> settlementService.markPaid(other.getSettlementId(), "PAID", ADMIN, 485L, "REF-42", null)).getCode());
        assertEquals("PENDING", settlementMapperRow(SELLER_B).getStatus());
        assertEquals(1, count("settlement_payout"));
    }

    @Test
    void paidStatementRemainsFrozenAndRefundDeductsOnlyOnceFromNextPeriod() {
        insertRule("R1", "PLATFORM", "*", 300);
        insertOrder("O1", SELLER_A, 10000L, "COMPLETED", T1);
        settlementService.generate(PERIOD, ADMIN);
        String original = onlySettlement().getSettlementId();
        settlementService.markPaid(original, "PAID", ADMIN, 9700L, "TEST-ORIGINAL", null);
        insertRefund("RF1", "O1", "SUCCESS");
        insertOrder("O2", SELLER_A, 5000L, "COMPLETED", NEXT_MONTH_START);
        settlementService.generate("2024-12", ADMIN);
        settlementService.generate("2024-12", ADMIN);
        var before = settlementService.list(SELLER_A, PERIOD, 1, 20).getList().get(0);
        var after = settlementService.list(SELLER_A, "2024-12", 1, 20).getList().get(0);
        assertEquals(9700L, before.getNetAmount());
        assertEquals(4850L - 97L, after.getNetAmount());
    }

    @Test
    @DisplayName("没有任何规则时费率为 0，佣金为 0，应结等于实付")
    void noRuleMeansZeroCommission() {
        insertOrder("O1", SELLER_A, 8800L, "COMPLETED", T1);

        settlementService.generate(PERIOD, ADMIN);

        Settlement settlement = onlySettlement();
        assertEquals(0L, settlement.getCommissionAmount());
        assertEquals(8800L, settlement.getNetAmount());
    }

    @Test
    @DisplayName("周期窗口按完成时间取半开区间：月末最后一秒在内，次月零点在外")
    void periodWindowRespectsBoundaries() {
        insertRule("R1", "PLATFORM", "*", 300);
        insertOrder("O1", SELLER_A, 10000L, "COMPLETED", MONTH_END);
        insertOrder("O2", SELLER_B, 20000L, "COMPLETED", NEXT_MONTH_START);

        settlementService.generate(PERIOD, ADMIN);

        assertEquals(1, count("settlement"));
        assertEquals(10000L, onlySettlement().getGrossAmount());
    }

    @Test
    @DisplayName("非法周期返回 10001")
    void invalidPeriodIsRejected() {
        BizException exception = assertThrows(BizException.class,
                () -> settlementService.generate("2026-13", ADMIN));
        assertEquals(ErrorCode.PARAM_INVALID, exception.getCode());
    }

    @Test
    @DisplayName("标记打款：PENDING 可流转 PAID，重复打款 60016，不存在 60015，非 PAID 目标 10001")
    void markPaidGuards() {
        insertOrder("O1", SELLER_A, 10000L, "COMPLETED", T1);
        settlementService.generate(PERIOD, ADMIN);
        String settlementId = onlySettlement().getSettlementId();

        SettlementResponse paid = settlementService.markPaid(settlementId, "PAID", ADMIN, onlySettlement().getNetAmount(), "TEST-" + settlementId, null);
        assertEquals(Settlement.STATUS_PAID, paid.getStatus());

        BizException again = assertThrows(BizException.class,
                () -> settlementService.markPaid(settlementId, "PAID", ADMIN, onlySettlement().getNetAmount(), "TEST-" + settlementId, null));
        assertEquals(ErrorCode.SETTLEMENT_STATE_INVALID, again.getCode());

        BizException missing = assertThrows(BizException.class,
                () -> settlementService.markPaid("ST-NOT-EXISTS", "PAID", ADMIN, 10000L, "TEST-MISSING", null));
        assertEquals(ErrorCode.SETTLEMENT_NOT_FOUND, missing.getCode());

        BizException badTarget = assertThrows(BizException.class,
                () -> settlementService.markPaid(settlementId, "CONFIRMED", ADMIN, 10000L, "TEST-BAD", null));
        assertEquals(ErrorCode.PARAM_INVALID, badTarget.getCode());
    }

    @Test
    @DisplayName("卖家列表只含自己的结算单，管理员可按周期过滤")
    void sellerListIsIsolated() {
        insertRule("R1", "PLATFORM", "*", 300);
        insertOrder("O1", SELLER_A, 10000L, "COMPLETED", T1);
        insertOrder("O2", SELLER_B, 20000L, "COMPLETED", T1);
        settlementService.generate(PERIOD, ADMIN);

        PageResult<SettlementResponse> sellerA = settlementService.listForSeller(SELLER_A, null, 1, 20);
        assertEquals(1, sellerA.getTotal());
        assertEquals(SELLER_A, sellerA.getList().get(0).getSellerId());

        PageResult<SettlementResponse> adminAll = settlementService.list(null, PERIOD, 1, 20);
        assertEquals(2, adminAll.getTotal());

        PageResult<SettlementResponse> adminFiltered = settlementService.list(SELLER_B, null, 1, 20);
        assertEquals(1, adminFiltered.getTotal());
        assertEquals(SELLER_B, adminFiltered.getList().get(0).getSellerId());
    }

    private Settlement onlySettlement() {
        List<Settlement> rows = jdbcTemplate.query(
                "SELECT settlement_id, seller_id, period_start, period_end, gross_amount,"
                        + " commission_amount, net_amount, status FROM settlement",
                (rs, rowNum) -> {
                    Settlement settlement = new Settlement();
                    settlement.setSettlementId(rs.getString("settlement_id"));
                    settlement.setSellerId(rs.getString("seller_id"));
                    settlement.setGrossAmount(rs.getLong("gross_amount"));
                    settlement.setCommissionAmount(rs.getLong("commission_amount"));
                    settlement.setNetAmount(rs.getLong("net_amount"));
                    settlement.setStatus(rs.getString("status"));
                    return settlement;
                });
        assertEquals(1, rows.size(), "应只有一张结算单");
        return rows.get(0);
    }

    private Settlement settlementMapperRow(String sellerId) {
        Map<String, Object> row = jdbcTemplate.queryForMap(
                "SELECT settlement_id, gross_amount, commission_amount, net_amount, status"
                        + " FROM settlement WHERE seller_id = ?", sellerId);
        Settlement settlement = new Settlement();
        settlement.setSettlementId((String) row.get("settlement_id"));
        settlement.setGrossAmount(((Number) row.get("gross_amount")).longValue());
        settlement.setCommissionAmount(((Number) row.get("commission_amount")).longValue());
        settlement.setNetAmount(((Number) row.get("net_amount")).longValue());
        settlement.setStatus((String) row.get("status"));
        return settlement;
    }

    private int count(String table) {
        return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM " + table, Integer.class);
    }

    private void insertRule(String ruleId, String scope, String targetId, int rate) {
        jdbcTemplate.update("INSERT INTO commission_rule (rule_id, scope, target_id, rate)"
                + " VALUES (?, ?, ?, ?)", ruleId, scope, targetId, rate);
    }

    private void insertOrder(String orderId, String sellerId, long payAmount, String status,
                             LocalDateTime updatedAt) {
        jdbcTemplate.update("INSERT INTO mall_order.`order`"
                        + " (order_id, seller_id, pay_amount, status, updated_at, is_deleted)"
                        + " VALUES (?, ?, ?, ?, ?, 0)",
                orderId, sellerId, payAmount, status, Timestamp.valueOf(updatedAt));
    }

    private void insertRefund(String refundId, String orderId, String status) {
        jdbcTemplate.update("INSERT INTO refund"
                        + " (refund_id, payment_id, order_id, buyer_id, seller_id, amount, status, is_deleted)"
                        + " VALUES (?, 'PAY-X', ?, 'B1', 'S1', 100, ?, 0)",
                refundId, orderId, status);
    }
}
