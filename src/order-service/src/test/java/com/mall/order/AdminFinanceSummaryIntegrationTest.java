package com.mall.order;

import com.mall.order.client.CartClient;
import com.mall.order.client.ProductClient;
import com.mall.order.client.UserClient;
import com.mall.order.common.BizException;
import com.mall.order.common.ErrorCode;
import com.mall.order.common.PageResult;
import com.mall.order.config.InternalTokenVerifier;
import com.mall.order.controller.OrderController;
import com.mall.order.dto.response.AdminFinanceSummaryResponse;
import com.mall.order.dto.response.OrderListItem;
import com.mall.order.service.OrderService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

/**
 * 管理员资金/风控汇总（任务：ADMIN 资金口径）集成测试。
 *
 * <p>造 100+ 订单、多个买家、多状态与逻辑删除数据，校验 SQL 聚合是全量、不分页的；
 * 同时校验新增的 ADMIN 端点只认 X-User-Id + X-User-Role=ADMIN，普通买家/卖家被拒。
 * 另外覆盖 seller 视角订单列表的收货信息快照契约（receiver + 手机号掩码，不附完整地址）。
 */
@SpringBootTest
@ActiveProfiles("test")
class AdminFinanceSummaryIntegrationTest {

    private static final String AD1 = "2026-11-01";
    private static final String AD2 = "2026-11-02";
    private static final String ADMIN = "U90000";

    @Autowired
    private OrderService orderService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @MockBean
    private CartClient cartClient;

    @MockBean
    private ProductClient productClient;

    @MockBean
    private UserClient userClient;

    @BeforeEach
    void cleanDatabase() {
        jdbcTemplate.update("DELETE FROM audit_log");
        jdbcTemplate.update("DELETE FROM order_evaluation");
        jdbcTemplate.update("DELETE FROM order_compensation");
        jdbcTemplate.update("DELETE FROM order_idempotency");
        jdbcTemplate.update("DELETE FROM order_item");
        jdbcTemplate.update("DELETE FROM `order`");
    }

    // ------------------------------------------------------------------ 资金汇总

    @Test
    void adminFinanceSummaryAggregatesAllNonDeletedOrdersWithoutPagination() {
        // B1：PAID 1000（11-01）、SHIPPED 2000（11-01）、COMPLETED 500（11-02）、REFUNDED 700（11-02）
        insertOrder("OA001", "OB1", "OS1", "PAID", 1000L, at("2026-10-31", 9, 0), at(AD1, 10, 0), 0);
        insertOrder("OA002", "OB1", "OS1", "SHIPPED", 2000L, at(AD1, 9, 5), at(AD1, 11, 0), 0);
        insertOrder("OA003", "OB1", "OS2", "COMPLETED", 500L, at(AD2, 8, 0), at(AD2, 12, 0), 0);
        insertOrder("OA004", "OB2", "OS2", "REFUNDED", 700L, at(AD2, 8, 30), at(AD2, 13, 0), 0);
        // 未支付：计入 orderCount/daily 订单数，不计入 paidAmount
        insertOrder("OA005", "OB2", "OS1", "PENDING_PAYMENT", 300L, at(AD1, 14, 0), null, 0);
        // 已取消：计入 orderCount 与 cancelledCount，不计入金额，不计入 daily 订单数
        insertOrder("OA006", "OB2", "OS1", "CANCELLED", 400L, at(AD2, 15, 0), null, 0);
        // 逻辑删除：任何口径都不能计入
        insertOrder("OA007", "OB2", "OS1", "PAID", 9000L, at(AD1, 16, 0), at(AD1, 16, 30), 1);
        insertOrder("OA008", "OB1", "OS2", "COMPLETED", 9000L, at(AD2, 16, 0), at(AD2, 16, 30), 1);

        // 把总量推过 100 条，验证聚合不分页（列表接口默认 size=20 也拿不到全部）
        for (int i = 0; i < 110; i++) {
            insertOrder(String.format("OB%03d", i), "OB3", "OS3", "CANCELLED", 10L,
                    at(AD1, 17, 0), null, 0);
        }

        AdminFinanceSummaryResponse summary = orderService.adminFinanceSummary();

        assertThat(summary.getOrderCount()).isEqualTo(6L + 110L);
        assertThat(summary.getGmv()).isEqualTo(1000L + 2000L + 500L + 700L + 300L + 400L + 110L * 10L);
        // paid 状态：PAID / SHIPPED / COMPLETED / REFUNDED
        assertThat(summary.getPaidCount()).isEqualTo(4L);
        assertThat(summary.getPaidAmount()).isEqualTo(1000L + 2000L + 500L + 700L);
        assertThat(summary.getCancelledCount()).isEqualTo(1L + 110L);

        assertThat(summary.getByStatus())
                .extracting(AdminFinanceSummaryResponse.StatusCount::getStatus,
                        AdminFinanceSummaryResponse.StatusCount::getCount)
                .containsExactlyInAnyOrder(
                        org.assertj.core.groups.Tuple.tuple("PAID", 1L),
                        org.assertj.core.groups.Tuple.tuple("SHIPPED", 1L),
                        org.assertj.core.groups.Tuple.tuple("COMPLETED", 1L),
                        org.assertj.core.groups.Tuple.tuple("REFUNDED", 1L),
                        org.assertj.core.groups.Tuple.tuple("PENDING_PAYMENT", 1L),
                        org.assertj.core.groups.Tuple.tuple("CANCELLED", 111L));

        // daily 按 paid_at 归日（不是 created_at），且只统计 paid 状态
        assertThat(summary.getDaily())
                .extracting(AdminFinanceSummaryResponse.DailyAmount::getDay,
                        AdminFinanceSummaryResponse.DailyAmount::getCount,
                        AdminFinanceSummaryResponse.DailyAmount::getAmount)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple(AD1, 2L, 3000L),
                        org.assertj.core.groups.Tuple.tuple(AD2, 2L, 1200L));
    }

    @Test
    void adminFinanceSummaryReturnsZeroesWhenNoOrders() {
        AdminFinanceSummaryResponse summary = orderService.adminFinanceSummary();

        assertThat(summary.getGmv()).isZero();
        assertThat(summary.getOrderCount()).isZero();
        assertThat(summary.getPaidCount()).isZero();
        assertThat(summary.getPaidAmount()).isZero();
        assertThat(summary.getCancelledCount()).isZero();
        assertThat(summary.getByStatus()).isEmpty();
        assertThat(summary.getDaily()).isEmpty();
    }

    // ------------------------------------------------------------------ ADMIN 端点鉴权

    @Test
    void adminEndpointRequiresHeadersRoleAndRejectsBuyerAndSeller() {
        OrderController controller = controller();
        insertOrder("OA001", "OB1", "OS1", "PAID", 1000L, at("2026-10-31", 9, 0), at(AD1, 10, 0), 0);

        assertAdminOnly(() -> controller.adminFinanceSummary(null, null));
        assertThatThrownBy(() -> controller.adminFinanceSummary(" ", "ADMIN"))
                .isInstanceOf(BizException.class)
                .satisfies(e -> assertThat(((BizException) e).getCode()).isEqualTo(ErrorCode.UNAUTHORIZED));
        assertAdminOnly(() -> controller.adminFinanceSummary(ADMIN, null));
        assertAdminOnly(() -> controller.adminFinanceSummary(ADMIN, " "));
        assertAdminOnly(() -> controller.adminFinanceSummary(ADMIN, "BUYER"));
        assertAdminOnly(() -> controller.adminFinanceSummary(ADMIN, "SELLER"));
        assertAdminOnly(() -> controller.adminFinanceSummary(ADMIN, "USER"));
        // 伪造 role 大小写/前后空格应被严格比对拒绝
        assertAdminOnly(() -> controller.adminFinanceSummary(ADMIN, "admin"));
        assertAdminOnly(() -> controller.adminFinanceSummary(ADMIN, " ADMIN"));

        assertThat(controller.adminFinanceSummary(ADMIN, "ADMIN").getData().getOrderCount())
                .isEqualTo(1L);
    }

    // ------------------------------------------------------------------ 列表收货信息契约

    @Test
    void sellerListExposesReceiverAndMaskedPhoneWithoutFullAddress() {
        insertOrder("OA001", "OB1", "OS1", "PAID", 1000L, at("2026-10-31", 9, 0), at(AD1, 10, 0), 0);

        PageResult<OrderListItem> page = orderService.list("OS1", "seller", null, 1, 20);
        assertThat(page.list()).hasSize(1);
        OrderListItem item = page.list().get(0);
        assertThat(item.getAddressSnapshot()).isNotNull();
        assertThat(item.getAddressSnapshot().getReceiver()).isEqualTo("收货人");
        assertThat(item.getAddressSnapshot().getPhone()).isEqualTo("138****8888");
        assertThat(item.getAddressSnapshot().getFullAddress()).isNull();
    }

    @Test
    void buyerListAndOtherSellerListDoNotLeakAddress() {
        insertOrder("OA001", "OB1", "OS1", "PAID", 1000L, at("2026-10-31", 9, 0), at(AD1, 10, 0), 0);

        PageResult<OrderListItem> buyerPage = orderService.list("OB1", "buyer", null, 1, 20);
        assertThat(buyerPage.list()).hasSize(1);
        assertThat(buyerPage.list().get(0).getAddressSnapshot()).isNull();

        PageResult<OrderListItem> otherSellerPage = orderService.list("OS2", "seller", null, 1, 20);
        assertThat(otherSellerPage.list()).isEmpty();
    }

    @Test
    void shortPhoneIsFullyMaskedAndNullPhoneIsTolerated() {
        insertOrder("OA001", "OB1", "OS1", "PAID", 1000L, at("2026-10-31", 9, 0), at(AD1, 10, 0), 0);
        jdbcTemplate.update("UPDATE `order` SET receiver_phone = '12345' WHERE order_id = 'OA001'");
        PageResult<OrderListItem> page = orderService.list("OS1", "seller", null, 1, 20);
        assertThat(page.list().get(0).getAddressSnapshot().getPhone()).isEqualTo("*****");
        jdbcTemplate.update("UPDATE `order` SET receiver_phone = '' WHERE order_id = 'OA001'");
        assertThat(orderService.list("OS1", "seller", null, 1, 20).list().get(0)
                .getAddressSnapshot().getPhone()).isEqualTo("*");
    }

    // ------------------------------------------------------------------ 工具

    private OrderController controller() {
        return new OrderController(orderService, mock(InternalTokenVerifier.class));
    }

    private static void assertAdminOnly(Runnable action) {
        assertThatThrownBy(action::run)
                .isInstanceOf(BizException.class)
                .satisfies(e -> assertThat(((BizException) e).getCode()).isEqualTo(ErrorCode.FORBIDDEN));
    }

    private void insertOrder(String orderId, String buyerId, String sellerId, String status,
                             long amount, LocalDateTime createdAt, LocalDateTime paidAt, int deleted) {
        jdbcTemplate.update("INSERT INTO `order`(order_id, order_no, buyer_id, seller_id, status, "
                        + "total_amount, pay_amount, receiver, receiver_phone, address_full, tracking_no, "
                        + "expire_at, paid_at, created_at, updated_at, created_by, updated_by, "
                        + "is_deleted, inventory_released) "
                        + "VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,0)",
                orderId, "NO" + orderId, buyerId, sellerId, status, amount, amount,
                "收货人", "13800008888", "天津市 红桥区 某路 1 号", null,
                createdAt.plusMinutes(30), paidAt, createdAt, createdAt, buyerId, buyerId, deleted);
    }

    private static LocalDateTime at(String day, int hour, int minute) {
        return LocalDateTime.parse(day + "T" + (hour < 10 ? "0" : "") + hour + ":"
                + (minute < 10 ? "0" : "") + minute + ":00");
    }
}
