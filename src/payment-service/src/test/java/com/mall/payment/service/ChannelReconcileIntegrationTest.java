package com.mall.payment.service;

import com.mall.payment.common.BizException;
import com.mall.payment.common.ChannelType;
import com.mall.payment.common.ErrorCode;
import com.mall.payment.common.PageResult;
import com.mall.payment.entity.ReconcileBatch;
import com.mall.payment.entity.ReconcileDifference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 渠道对账集成测试（#137）。
 *
 * <p>导入账单 CSV 产出三类差异（LOCAL_ONLY / CHANNEL_ONLY / AMOUNT_MISMATCH），
 * 覆盖 PAYMENT 与 REFUND；全程对 payment/refund 只读；差异可标记处理且不可重复处理；
 * CSV 表头/列数/金额/空账单校验。
 */
@SpringBootTest
class ChannelReconcileIntegrationTest {

    private static final String ADMIN = "ADMIN01";
    private static final String HEADER = "trade_no,bill_type,out_trade_no,amount_fen,status,trade_time";

    @Autowired
    private ChannelReconcileService reconcileService;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() {
        jdbcTemplate.update("DELETE FROM reconcile_difference");
        jdbcTemplate.update("DELETE FROM reconcile_batch");
        jdbcTemplate.update("DELETE FROM refund");
        jdbcTemplate.update("DELETE FROM payment");
        insertPayment("PAYA", "PA", "OA", 11800L);
        insertPayment("PAYB", "PB", "OB", 5000L);
        insertPayment("PAYC", "PC", "OC", 3000L);
        insertRefund("R1", "PAYA", "OA", 2000L, "CR1");
        insertRefund("R2", "PAYB", "OB", 1000L, "CR2");
        insertRefund("R3", "PAYC", "OC", 800L, null);
    }

    private InputStream csv(String body) {
        return new ByteArrayInputStream((HEADER + "\n" + body).getBytes(StandardCharsets.UTF_8));
    }

    @Test
    @DisplayName("三类差异齐全：PAYMENT/REFUND 各有 CHANNEL_ONLY/LOCAL_ONLY/AMOUNT_MISMATCH，FAILED 行忽略")
    void producesAllThreeDifferenceTypes() {
        String body = """
                TA,PAYMENT,PA,11800,SUCCESS,2026-11-17 12:00:00
                TB,PAYMENT,PB,6000,SUCCESS,2026-11-17 12:00:00
                TX,PAYMENT,PX,900,SUCCESS,2026-11-17 12:00:00
                CR1,REFUND,R1,2000,SUCCESS,2026-11-17 12:00:00
                CR2,REFUND,R2,1500,SUCCESS,2026-11-17 12:00:00
                CRX,REFUND,RX,700,SUCCESS,2026-11-17 12:00:00
                TIGNORED,PAYMENT,PIGNORED,100,FAILED,2026-11-17 12:00:00
                """;
        List<Map<String, Object>> paymentsBefore = snapshot("payment");
        List<Map<String, Object>> refundsBefore = snapshot("refund");

        ReconcileBatch batch = reconcileService.importBill(ADMIN, ChannelType.ALIPAY, "bill.csv", csv(body));

        assertEquals(6, batch.getTotalRows().intValue());
        assertEquals(6, batch.getDiffCount().intValue());
        assertEquals(6, count("reconcile_difference"));
        assertEquals(1, count("reconcile_batch"));

        assertEquals(1, countDiff("PAYMENT", "AMOUNT_MISMATCH"));
        assertEquals(1, countDiff("PAYMENT", "CHANNEL_ONLY"));
        assertEquals(1, countDiff("PAYMENT", "LOCAL_ONLY"));
        assertEquals(1, countDiff("REFUND", "AMOUNT_MISMATCH"));
        assertEquals(1, countDiff("REFUND", "CHANNEL_ONLY"));
        assertEquals(1, countDiff("REFUND", "LOCAL_ONLY"));

        // 全程只读：payment/refund 一行都不改
        assertEquals(paymentsBefore, snapshot("payment"));
        assertEquals(refundsBefore, snapshot("refund"));
    }

    @Test
    @DisplayName("金额一致的账单不产生差异")
    void fullyMatchedBillHasNoDifference() {
        String body = """
                TA,PAYMENT,PA,11800,SUCCESS,2026-11-17 12:00:00
                TB,PAYMENT,PB,5000,SUCCESS,2026-11-17 12:00:00
                TC,PAYMENT,PC,3000,SUCCESS,2026-11-17 12:00:00
                CR1,REFUND,R1,2000,SUCCESS,2026-11-17 12:00:00
                CR2,REFUND,R2,1000,SUCCESS,2026-11-17 12:00:00
                UNKNOWN,REFUND,R3,800,SUCCESS,2026-11-17 12:00:00
                """;
        ReconcileBatch batch = reconcileService.importBill(ADMIN, ChannelType.ALIPAY, "ok.csv", csv(body));
        assertEquals(0, batch.getDiffCount().intValue());
        assertEquals(0, count("reconcile_difference"));
    }

    @Test
    @DisplayName("差异标记处理：PENDING→RESOLVED，重复处理报错")
    void resolveDifferenceOnce() {
        ReconcileBatch batch = reconcileService.importBill(ADMIN, ChannelType.ALIPAY, "b.csv",
                csv("TX,PAYMENT,PX,900,SUCCESS,2026-11-17 12:00:00\n"));
        PageResult<ReconcileDifference> page =
                reconcileService.listDifferences(batch.getBatchId(), null, "PENDING", 1, 20);
        Long diffId = page.getList().get(0).getDifferenceId();

        ReconcileDifference resolved = reconcileService.resolve(diffId, ADMIN, "已人工核销");
        assertEquals("RESOLVED", resolved.getStatus());
        assertEquals(ADMIN, resolved.getResolvedBy());

        BizException exception = assertThrows(BizException.class,
                () -> reconcileService.resolve(diffId, ADMIN, "再处理一次"));
        assertEquals(ErrorCode.PARAM_INVALID, exception.getCode());
    }

    @Test
    @DisplayName("差异列表按类型与状态过滤")
    void listDifferencesFilters() {
        ReconcileBatch batch = reconcileService.importBill(ADMIN, ChannelType.ALIPAY, "b.csv",
                csv("TX,PAYMENT,PX,900,SUCCESS,2026-11-17 12:00:00\n"));
        PageResult<ReconcileDifference> channelOnly =
                reconcileService.listDifferences(batch.getBatchId(), "CHANNEL_ONLY", null, 1, 20);
        assertTrue(channelOnly.getList().stream().allMatch(d -> "CHANNEL_ONLY".equals(d.getDiffType())));
        assertEquals(1, channelOnly.getTotal());

        BizException badType = assertThrows(BizException.class,
                () -> reconcileService.listDifferences(batch.getBatchId(), "UNKNOWN", null, 1, 20));
        assertEquals(ErrorCode.PARAM_INVALID, badType.getCode());
    }

    @Test
    @DisplayName("非法表头拒绝")
    void rejectsBadHeader() {
        InputStream bad = new ByteArrayInputStream(
                "wrong,header\na,b\n".getBytes(StandardCharsets.UTF_8));
        BizException exception = assertThrows(BizException.class,
                () -> reconcileService.importBill(ADMIN, ChannelType.ALIPAY, "bad.csv", bad));
        assertEquals(ErrorCode.PARAM_INVALID, exception.getCode());
        assertEquals(0, count("reconcile_batch"));
    }

    @Test
    @DisplayName("列数不是 6 拒绝")
    void rejectsBadColumnCount() {
        BizException exception = assertThrows(BizException.class,
                () -> reconcileService.importBill(ADMIN, ChannelType.ALIPAY, "bad.csv",
                        csv("TA,PAYMENT,PA,11800,SUCCESS\n")));
        assertEquals(ErrorCode.PARAM_INVALID, exception.getCode());
    }

    @Test
    @DisplayName("金额非整数分拒绝")
    void rejectsNonIntegerAmount() {
        BizException exception = assertThrows(BizException.class,
                () -> reconcileService.importBill(ADMIN, ChannelType.ALIPAY, "bad.csv",
                        csv("TA,PAYMENT,PA,118.00,SUCCESS,2026-11-17 12:00:00\n")));
        assertEquals(ErrorCode.PARAM_INVALID, exception.getCode());
    }

    @Test
    @DisplayName("没有 SUCCESS 行拒绝")
    void rejectsNoSuccessRows() {
        BizException exception = assertThrows(BizException.class,
                () -> reconcileService.importBill(ADMIN, ChannelType.ALIPAY, "bad.csv",
                        csv("TA,PAYMENT,PA,11800,FAILED,2026-11-17 12:00:00\n")));
        assertEquals(ErrorCode.PARAM_INVALID, exception.getCode());
    }

    private void insertPayment(String paymentId, String payNo, String orderId, long amount) {
        jdbcTemplate.update("""
                INSERT INTO payment (
                    payment_id, pay_no, order_id, buyer_id, seller_id, channel, amount,
                    refunded_amount, status, paid_at, created_by, updated_by, is_deleted
                ) VALUES (?, ?, ?, 'U1', 'S1', 'ALIPAY', ?, 0, 'SUCCESS', CURRENT_TIMESTAMP, 'U1', 'U1', 0)
                """, paymentId, payNo, orderId, amount);
    }

    private void insertRefund(String refundId, String paymentId, String orderId,
                              long amount, String channelRefundNo) {
        jdbcTemplate.update("""
                INSERT INTO refund (
                    refund_id, payment_id, order_id, buyer_id, seller_id, amount, reason,
                    status, channel_refund_no, created_by, updated_by, is_deleted
                ) VALUES (?, ?, ?, 'U1', 'S1', ?, '对账测试', 'SUCCESS', ?, 'U1', 'U1', 0)
                """, refundId, paymentId, orderId, amount, channelRefundNo);
    }

    private List<Map<String, Object>> snapshot(String table) {
        if ("payment".equals(table)) {
            return jdbcTemplate.queryForList(
                    "SELECT payment_id, amount, refunded_amount, status FROM payment ORDER BY payment_id");
        }
        return jdbcTemplate.queryForList(
                "SELECT refund_id, amount, status, channel_refund_no FROM refund ORDER BY refund_id");
    }

    private int countDiff(String billType, String diffType) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM reconcile_difference WHERE bill_type = ? AND diff_type = ?",
                Integer.class, billType, diffType);
        return count == null ? 0 : count;
    }

    private int count(String table) {
        Integer count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM " + table, Integer.class);
        return count == null ? 0 : count;
    }
}