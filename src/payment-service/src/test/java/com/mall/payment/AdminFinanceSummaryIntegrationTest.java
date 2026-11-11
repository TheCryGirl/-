package com.mall.payment;

import com.mall.payment.client.OrderClient;
import com.mall.payment.client.ProductClient;
import com.mall.payment.common.BizException;
import com.mall.payment.common.ErrorCode;
import com.mall.payment.controller.PaymentController;
import com.mall.payment.dto.response.AdminFinanceSummaryResponse;
import com.mall.payment.service.FinanceSummaryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.groups.Tuple.tuple;

/**
 * 管理员资金/风控汇总（退款口径）集成测试。
 *
 * <p>退款聚合必须走 SQL 全量统计（is_deleted = 0、不分页）：
 * 成功退款按 audited_at 归日计入；待审退款按金额计入 pendingRefundAmount。
 * 同时校验 ADMIN 端点只认 X-User-Id + X-User-Role=ADMIN。
 */
@SpringBootTest
class AdminFinanceSummaryIntegrationTest {

    private static final String AD1 = "2026-11-01";
    private static final String AD2 = "2026-11-02";
    private static final String ADMIN = "U90000";

    @Autowired
    private FinanceSummaryService financeSummaryService;

    @Autowired
    private PaymentController paymentController;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @MockBean
    private OrderClient orderClient;

    @MockBean
    private ProductClient productClient;

    @BeforeEach
    void cleanDatabase() {
        jdbcTemplate.update("DELETE FROM refund_notification_task");
        jdbcTemplate.update("DELETE FROM after_sale_ticket");
        jdbcTemplate.update("DELETE FROM refund");
        jdbcTemplate.update("DELETE FROM payment_callback");
        jdbcTemplate.update("DELETE FROM payment");

        insertRefund("RF001", "PAY1", "O1", "OB1", "OS1", 1000L, "SUCCESS", at(AD1, 10, 0), "2026-10-31 09:00:00", 0);
        insertRefund("RF002", "PAY1", "O1", "OB1", "OS1", 2000L, "SUCCESS", at(AD1, 11, 0), "2026-11-01 09:10:00", 0);
        insertRefund("RF003", "PAY2", "O2", "OB2", "OS1", 500L, "SUCCESS", at(AD2, 12, 0), "2026-11-02 08:00:00", 0);
        insertRefund("RF004", "PAY2", "O2", "OB2", "OS1", 300L, "PENDING", null, "2026-11-02 08:30:00", 0);
        insertRefund("RF005", "PAY3", "O3", "OB2", "OS2", 40L, "PENDING", null, "2026-11-01 18:00:00", 0);
        insertRefund("RF006", "PAY3", "O3", "OB2", "OS2", 600L, "REJECTED", at(AD2, 13, 0), "2026-11-02 09:00:00", 0);
        insertRefund("RF007", "PAY4", "O4", "OB3", "OS2", 700L, "FAILED", at(AD2, 14, 0), "2026-11-02 10:00:00", 0);
        // 逻辑删除：不进入任何口径
        insertRefund("RF008", "PAY4", "O4", "OB3", "OS2", 9000L, "SUCCESS", at(AD1, 15, 0), "2026-11-01 10:30:00", 1);
        insertRefund("RF009", "PAY4", "O4", "OB3", "OS2", 9000L, "PENDING", null, "2026-11-01 10:40:00", 1);
    }

    @Test
    void financeSummaryAggregatesRefundsFromSqlWithoutPagination() {
        // 把退款单量推过 100 条，确认聚合是 SQL 全量而不是分页取一页
        for (int i = 0; i < 110; i++) {
            insertRefund(String.format("RFP%03d", i), "PAY9", "O9", "OB9", "OS9", 10L, "PENDING", null,
                    "2026-11-03 09:00:00", 0);
        }

        AdminFinanceSummaryResponse summary = financeSummaryService.summary();

        assertThat(summary.getRefundedCount()).isEqualTo(3L);
        assertThat(summary.getRefundedAmount()).isEqualTo(1000L + 2000L + 500L);
        // PENDING 才计入待退；REJECTED / FAILED 都不计入
        assertThat(summary.getPendingRefundAmount()).isEqualTo(300L + 40L + 110L * 10L);

        assertThat(summary.getDaily())
                .extracting(AdminFinanceSummaryResponse.DailyRefund::getDay,
                        AdminFinanceSummaryResponse.DailyRefund::getRefund)
                .containsExactly(tuple(AD1, 3000L), tuple(AD2, 500L));
    }

    @Test
    void financeSummaryReturnsZeroesWhenNoRefunds() {
        jdbcTemplate.update("DELETE FROM refund");

        AdminFinanceSummaryResponse summary = financeSummaryService.summary();

        assertThat(summary.getRefundedCount()).isZero();
        assertThat(summary.getRefundedAmount()).isZero();
        assertThat(summary.getPendingRefundAmount()).isZero();
        assertThat(summary.getDaily()).isEmpty();
    }

    @Test
    void adminEndpointRejectsNonAdminCallers() {
        assertThatThrownBy(() -> paymentController.adminFinanceSummary(null, null))
                .isInstanceOf(BizException.class)
                .satisfies(e -> assertThat(((BizException) e).getCode()).isEqualTo(ErrorCode.FORBIDDEN));
        assertThatThrownBy(() -> paymentController.adminFinanceSummary(" ", "ADMIN"))
                .isInstanceOf(BizException.class)
                .satisfies(e -> assertThat(((BizException) e).getCode()).isEqualTo(ErrorCode.UNAUTHORIZED));
        assertThatThrownBy(() -> paymentController.adminFinanceSummary(ADMIN, null))
                .isInstanceOf(BizException.class)
                .satisfies(e -> assertThat(((BizException) e).getCode()).isEqualTo(ErrorCode.FORBIDDEN));
        assertThatThrownBy(() -> paymentController.adminFinanceSummary(ADMIN, "BUYER"))
                .isInstanceOf(BizException.class)
                .satisfies(e -> assertThat(((BizException) e).getCode()).isEqualTo(ErrorCode.FORBIDDEN));
        assertThatThrownBy(() -> paymentController.adminFinanceSummary(ADMIN, "SELLER"))
                .isInstanceOf(BizException.class)
                .satisfies(e -> assertThat(((BizException) e).getCode()).isEqualTo(ErrorCode.FORBIDDEN));
        // 严格比对：大小写、前后空格都不算 ADMIN
        assertThatThrownBy(() -> paymentController.adminFinanceSummary(ADMIN, "admin"))
                .isInstanceOf(BizException.class)
                .satisfies(e -> assertThat(((BizException) e).getCode()).isEqualTo(ErrorCode.FORBIDDEN));
        assertThatThrownBy(() -> paymentController.adminFinanceSummary(ADMIN, " ADMIN"))
                .isInstanceOf(BizException.class)
                .satisfies(e -> assertThat(((BizException) e).getCode()).isEqualTo(ErrorCode.FORBIDDEN));

        assertThat(paymentController.adminFinanceSummary(ADMIN, "ADMIN").getData().getRefundedCount())
                .isEqualTo(3L);
    }

    private void insertRefund(String refundId, String paymentId, String orderId, String buyerId, String sellerId,
                              long amount, String status,
                              java.time.LocalDateTime auditedAt, String createdAt, int deleted) {
        jdbcTemplate.update("INSERT INTO refund(refund_id, payment_id, order_id, buyer_id, seller_id, amount, "
                        + "reason, status, audited_by, audit_remark, audited_at, created_at, updated_at, "
                        + "created_by, updated_by, is_deleted) "
                        + "VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)",
                refundId, paymentId, orderId, buyerId, sellerId, amount,
                "不想要了", status, auditedAt == null ? null : "U90000", null,
                auditedAt, createdAt, createdAt, buyerId, buyerId, deleted);
    }

    private static java.time.LocalDateTime at(String day, int hour, int minute) {
        return java.time.LocalDateTime.parse(day + "T" + (hour < 10 ? "0" : "") + hour + ":"
                + (minute < 10 ? "0" : "") + minute + ":00");
    }
}
