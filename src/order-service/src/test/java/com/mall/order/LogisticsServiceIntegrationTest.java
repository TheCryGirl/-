package com.mall.order;

import com.mall.order.common.BizException;
import com.mall.order.common.ErrorCode;
import com.mall.order.common.OrderStatus;
import com.mall.order.dto.request.LogisticsTrackRequest;
import com.mall.order.dto.request.ShipOrderRequest;
import com.mall.order.dto.response.LogisticsTrackResponse;
import com.mall.order.dto.response.OrderDetailResponse;
import com.mall.order.dto.response.OrderLogisticsResponse;
import com.mall.order.mapper.OrderMapper;
import com.mall.order.service.AutoConfirmJob;
import com.mall.order.service.LogisticsService;
import com.mall.order.service.OrderService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 物流履约与自动确认收货（任务 #138）的集成测试。
 *
 * <p>覆盖 issue 验收清单里的每一条：缺字段、发货状态、轨迹排序与异常标记、
 * 未发货查物流、到期自动完成、双实例只处理一次、手动确认后调度不再处理。
 * 用 H2 隔离库，不碰开发数据库，也不连 Nacos（配置读取失败会回退默认 10 天）。
 */
@SpringBootTest
@ActiveProfiles("test")
class LogisticsServiceIntegrationTest {

    private static final String BUYER = "U13800001";
    private static final String SELLER = "U13800002";
    private static final String OTHER = "U13800003";
    private static final String ADMIN = "U13800009";
    private static final String ORDER_ID = "OLOG00000001";

    @Autowired
    private LogisticsService logisticsService;

    @Autowired
    private OrderService orderService;

    @Autowired
    private AutoConfirmJob autoConfirmJob;

    @Autowired
    private OrderMapper orderMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() {
        jdbcTemplate.update("DELETE FROM order_logistics_track");
        jdbcTemplate.update("DELETE FROM order_compensation");
        jdbcTemplate.update("DELETE FROM order_idempotency");
        jdbcTemplate.update("DELETE FROM order_item");
        jdbcTemplate.update("DELETE FROM `order`");
    }

    /* ---------- 发货 ---------- */

    @Test
    void shipWithoutExpressCompanyOrTrackingNoReturnsParamInvalid() {
        insertOrder(ORDER_ID, "PAID", null, null);

        ShipOrderRequest missingCompany = shipRequest(null, "SF12345678");
        assertThatThrownBy(() -> orderService.ship(ORDER_ID, SELLER, "SELLER", missingCompany))
                .isInstanceOf(BizException.class)
                .extracting(e -> ((BizException) e).getCode())
                .isEqualTo(ErrorCode.PARAM_INVALID);

        ShipOrderRequest missingTracking = shipRequest("SF", "  ");
        assertThatThrownBy(() -> orderService.ship(ORDER_ID, SELLER, "SELLER", missingTracking))
                .isInstanceOf(BizException.class)
                .extracting(e -> ((BizException) e).getCode())
                .isEqualTo(ErrorCode.PARAM_INVALID);

        // 非法编码同样按 10001 处理，不能靠自由文本绕过枚举
        ShipOrderRequest badCompany = shipRequest("SHUNFENG", "SF12345678");
        assertThatThrownBy(() -> orderService.ship(ORDER_ID, SELLER, "SELLER", badCompany))
                .isInstanceOf(BizException.class)
                .extracting(e -> ((BizException) e).getCode())
                .isEqualTo(ErrorCode.PARAM_INVALID);

        // 三次都没改成 SHIPPED，说明校验发生在状态迁移之前
        assertThat(orderMapper.selectById(ORDER_ID).getStatus()).isEqualTo(OrderStatus.PAID);
    }

    @Test
    void shipWritesExpressCompanyAndTrackingNoVisibleToBuyer() {
        insertOrder(ORDER_ID, "PAID", null, null);

        OrderDetailResponse detail = orderService.ship(ORDER_ID, SELLER, "SELLER",
                shipRequest("SF", "SF9988776655"));

        assertThat(detail.getStatus()).isEqualTo(OrderStatus.SHIPPED);
        assertThat(detail.getTrackingNo()).isEqualTo("SF9988776655");
        assertThat(detail.getExpressCompany()).isEqualTo("SF");
        assertThat(detail.getExpressCompanyLabel()).isEqualTo("顺丰速运");
        assertThat(detail.getShippedAt()).isNotBlank();

        OrderLogisticsResponse logistics = logisticsService.detail(ORDER_ID, BUYER, "BUYER");
        assertThat(logistics.isShipped()).isTrue();
        assertThat(logistics.getTrackingNo()).isEqualTo("SF9988776655");
        assertThat(logistics.getExpressCompanyLabel()).isEqualTo("顺丰速运");
        assertThat(logistics.getMessage()).isEqualTo("已发货，物流更新中");
    }

    @Test
    void shipRejectsOrderThatIsNotPaid() {
        insertOrder(ORDER_ID, "PENDING_PAYMENT", null, null);
        assertThatThrownBy(() -> orderService.ship(ORDER_ID, SELLER, "SELLER", shipRequest("SF", "SF1")))
                .isInstanceOf(BizException.class)
                .extracting(e -> ((BizException) e).getCode())
                .isEqualTo(ErrorCode.ORDER_STATE_INVALID);
    }

    /* ---------- 轨迹 ---------- */

    @Test
    void threeTracksAreListedInTimeOrderAndExceptionIsMarked() {
        insertOrder(ORDER_ID, "PAID", null, null);
        orderService.ship(ORDER_ID, SELLER, "SELLER", shipRequest("ZTO", "ZTO123456"));

        // 故意乱序录入：接口按 occurredAt 正序返回，与录入顺序无关
        logisticsService.append(ORDER_ID, SELLER, "SELLER",
                trackRequest("OUT_FOR_DELIVERY", "快件已到达派送点", "2026-11-15 09:00:00"));
        logisticsService.append(ORDER_ID, SELLER, "SELLER",
                trackRequest("COLLECTED", "快递员已揽收", "2026-11-13 10:00:00"));
        logisticsService.append(ORDER_ID, SELLER, "SELLER",
                trackRequest("EXCEPTION", "收件地址异常，请联系快递员", "2026-11-14 08:30:00"));

        OrderLogisticsResponse logistics = logisticsService.detail(ORDER_ID, BUYER, "BUYER");
        List<LogisticsTrackResponse> tracks = logistics.getTracks();

        assertThat(tracks).hasSize(3);
        assertThat(tracks).extracting(LogisticsTrackResponse::getStatus)
                .containsExactly("COLLECTED", "EXCEPTION", "OUT_FOR_DELIVERY");
        assertThat(tracks).extracting(LogisticsTrackResponse::getOccurredAt)
                .containsExactly("2026-11-13 10:00:00", "2026-11-14 08:30:00", "2026-11-15 09:00:00");
        // 只有异常件标红
        assertThat(tracks).extracting(LogisticsTrackResponse::isAbnormal)
                .containsExactly(false, true, false);
        assertThat(tracks.get(1).getStatusLabel()).isEqualTo("异常件");
        assertThat(logistics.getMessage()).isNull();
    }

    @Test
    void shippeOrderWithoutTracksReturnsEmptyListWithHint() {
        insertOrder(ORDER_ID, "PAID", null, null);
        orderService.ship(ORDER_ID, SELLER, "SELLER", shipRequest("YTO", "YTO0001"));

        OrderLogisticsResponse logistics = logisticsService.detail(ORDER_ID, BUYER, "BUYER");
        assertThat(logistics.getTracks()).isEmpty();
        assertThat(logistics.getMessage()).isEqualTo("已发货，物流更新中");
    }

    @Test
    void unshippedOrderReturnsEmptyListAndHintInsteadOfError() {
        insertOrder(ORDER_ID, "PAID", null, null);

        OrderLogisticsResponse logistics = logisticsService.detail(ORDER_ID, BUYER, "BUYER");
        assertThat(logistics.isShipped()).isFalse();
        assertThat(logistics.getTracks()).isEmpty();
        assertThat(logistics.getMessage()).isEqualTo("订单尚未发货，暂无物流信息");
    }

    @Test
    void trackCannotBeAppendedBeforeShipmentAndOnlySellerOrAdminMayAppend() {
        insertOrder(ORDER_ID, "PAID", null, null);
        assertThatThrownBy(() -> logisticsService.append(ORDER_ID, SELLER, "SELLER",
                trackRequest("COLLECTED", "还未发货就录轨迹", null)))
                .isInstanceOf(BizException.class)
                .extracting(e -> ((BizException) e).getCode())
                .isEqualTo(ErrorCode.ORDER_STATE_INVALID);

        orderService.ship(ORDER_ID, SELLER, "SELLER", shipRequest("SF", "SF0001"));

        assertThatThrownBy(() -> logisticsService.append(ORDER_ID, OTHER, "SELLER",
                trackRequest("COLLECTED", "别人的店", null)))
                .isInstanceOf(BizException.class)
                .extracting(e -> ((BizException) e).getCode())
                .isEqualTo(ErrorCode.FORBIDDEN);

        // 管理员可以补录任意订单
        assertThat(logisticsService.append(ORDER_ID, ADMIN, "ADMIN",
                trackRequest("IN_TRANSIT", "管理员补录", null)).getTrackId()).startsWith("T");
    }

    @Test
    void unknownTrackStatusIsRejectedWithParamInvalid() {
        insertOrder(ORDER_ID, "PAID", null, null);
        orderService.ship(ORDER_ID, SELLER, "SELLER", shipRequest("SF", "SF0002"));

        assertThatThrownBy(() -> logisticsService.append(ORDER_ID, SELLER, "SELLER",
                trackRequest("DELIVERED", "不存在的状态", null)))
                .isInstanceOf(BizException.class)
                .extracting(e -> ((BizException) e).getCode())
                .isEqualTo(ErrorCode.PARAM_INVALID);
    }

    @Test
    void otherBuyerCannotReadLogisticsOfSomeoneElsesOrder() {
        insertOrder(ORDER_ID, "PAID", null, null);
        orderService.ship(ORDER_ID, SELLER, "SELLER", shipRequest("SF", "SF0003"));

        assertThatThrownBy(() -> logisticsService.detail(ORDER_ID, OTHER, "BUYER"))
                .isInstanceOf(BizException.class)
                .extracting(e -> ((BizException) e).getCode())
                .isEqualTo(ErrorCode.FORBIDDEN);
    }

    /* ---------- 自动确认收货 ---------- */

    @Test
    void shippedOrderPastDeadlineIsAutoCompleted() {
        insertOrder(ORDER_ID, "PAID", null, null);
        orderService.ship(ORDER_ID, SELLER, "SELLER", shipRequest("SF", "SF0004"));
        // 造一个已过期的发货时间：默认阈值 10 天，这里回拨 11 天
        jdbcTemplate.update("UPDATE `order` SET shipped_at = ? WHERE order_id = ?",
                LocalDateTime.now().minusDays(11), ORDER_ID);

        assertThat(orderMapper.selectAutoConfirmCandidates(LocalDateTime.now().minusDays(10), 10))
                .contains(ORDER_ID);

        autoConfirmJob.scan();

        assertThat(orderMapper.selectById(ORDER_ID).getStatus()).isEqualTo(OrderStatus.COMPLETED);
    }

    @Test
    void orderInsideDeadlineIsNotAutoCompleted() {
        insertOrder(ORDER_ID, "PAID", null, null);
        orderService.ship(ORDER_ID, SELLER, "SELLER", shipRequest("SF", "SF0005"));
        jdbcTemplate.update("UPDATE `order` SET shipped_at = ? WHERE order_id = ?",
                LocalDateTime.now().minusDays(1), ORDER_ID);

        autoConfirmJob.scan();

        assertThat(orderMapper.selectById(ORDER_ID).getStatus()).isEqualTo(OrderStatus.SHIPPED);
    }

    @Test
    void twoSchedulerInstancesProcessSameOrderOnlyOnce() throws Exception {
        insertOrder(ORDER_ID, "PAID", null, null);
        orderService.ship(ORDER_ID, SELLER, "SELLER", shipRequest("SF", "SF0006"));
        jdbcTemplate.update("UPDATE `order` SET shipped_at = ? WHERE order_id = ?",
                LocalDateTime.now().minusDays(11), ORDER_ID);

        int instances = 2;
        ExecutorService pool = Executors.newFixedThreadPool(instances);
        CountDownLatch start = new CountDownLatch(1);
        List<Boolean> results;
        try {
            List<Future<Boolean>> futures = java.util.stream.IntStream.range(0, instances)
                    .mapToObj(i -> pool.submit(() -> {
                        start.await(30, TimeUnit.SECONDS);
                        // 两个独立实例：各自先查候选，再抢同一个 CAS
                        List<String> candidates = orderMapper.selectAutoConfirmCandidates(
                                LocalDateTime.now().minusDays(10), 10);
                        return candidates.contains(ORDER_ID) && orderService.autoConfirm(ORDER_ID);
                    })).toList();
            start.countDown();
            results = futures.stream().map(f -> {
                try {
                    return f.get(60, TimeUnit.SECONDS);
                } catch (Exception e) {
                    throw new IllegalStateException(e);
                }
            }).toList();
        } finally {
            pool.shutdownNow();
        }

        // 恰好一个实例抢到，另一个影响行数为 0 被跳过
        assertThat(results).filteredOn(Boolean::booleanValue).hasSize(1);
        assertThat(orderMapper.selectById(ORDER_ID).getStatus()).isEqualTo(OrderStatus.COMPLETED);
    }

    @Test
    void manualConfirmWinsAndSchedulerSkipsThatOrder() {
        insertOrder(ORDER_ID, "PAID", null, null);
        orderService.ship(ORDER_ID, SELLER, "SELLER", shipRequest("SF", "SF0007"));
        jdbcTemplate.update("UPDATE `order` SET shipped_at = ? WHERE order_id = ?",
                LocalDateTime.now().minusDays(11), ORDER_ID);

        // 买家先手动确认
        orderService.confirm(ORDER_ID, BUYER);
        assertThat(orderMapper.selectById(ORDER_ID).getStatus()).isEqualTo(OrderStatus.COMPLETED);

        // 调度再跑：候选查询已排除非 SHIPPED，autoConfirm 的 CAS 也会返回 false
        autoConfirmJob.scan();
        assertThat(orderService.autoConfirm(ORDER_ID)).isFalse();
        assertThat(orderMapper.selectById(ORDER_ID).getStatus()).isEqualTo(OrderStatus.COMPLETED);
    }

    @Test
    void signedTrackDoesNotAutoCompleteOrder() {
        insertOrder(ORDER_ID, "PAID", null, null);
        orderService.ship(ORDER_ID, SELLER, "SELLER", shipRequest("SF", "SF0008"));
        logisticsService.append(ORDER_ID, SELLER, "SELLER",
                trackRequest("SIGNED", "收件人已签收", null));

        // 签收是快递侧事实：没有到期的单不会因为出现 SIGNED 就完成
        autoConfirmJob.scan();
        assertThat(orderMapper.selectById(ORDER_ID).getStatus()).isEqualTo(OrderStatus.SHIPPED);
    }

    @Test
    void shippedOrderExposesRemainingMinutesAndExpiredOnesAreZero() {
        insertOrder(ORDER_ID, "PAID", null, null);
        orderService.ship(ORDER_ID, SELLER, "SELLER", shipRequest("SF", "SF0009"));

        OrderDetailResponse fresh = orderService.detail(ORDER_ID, BUYER, "BUYER");
        // 刚发货：默认 10 天 = 14400 分钟，允许少量执行耗时的误差
        assertThat(fresh.getAutoConfirmRemainingMinutes()).isBetween(14398L, 14400L);

        jdbcTemplate.update("UPDATE `order` SET shipped_at = ? WHERE order_id = ?",
                LocalDateTime.now().minusDays(11), ORDER_ID);
        assertThat(orderService.detail(ORDER_ID, BUYER, "BUYER").getAutoConfirmRemainingMinutes())
                .isZero();
    }

    /* ---------- 辅助 ---------- */

    private static ShipOrderRequest shipRequest(String company, String trackingNo) {
        ShipOrderRequest request = new ShipOrderRequest();
        request.setExpressCompany(company);
        request.setTrackingNo(trackingNo);
        return request;
    }

    private static LogisticsTrackRequest trackRequest(String status, String description, String occurredAt) {
        LogisticsTrackRequest request = new LogisticsTrackRequest();
        request.setStatus(status);
        request.setDescription(description);
        request.setOccurredAt(occurredAt);
        return request;
    }

    private void insertOrder(String orderId, String status, String expressCompany, String trackingNo) {
        LocalDateTime now = LocalDateTime.now();
        jdbcTemplate.update("INSERT INTO `order` (order_id, order_no, buyer_id, seller_id, status, total_amount, "
                        + "pay_amount, receiver, receiver_phone, address_full, tracking_no, express_company, "
                        + "shipped_at, expire_at, paid_at, created_at, updated_at, created_by, updated_by, "
                        + "is_deleted, inventory_released) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 0, 0)",
                orderId, "NO" + orderId, BUYER, SELLER, status, 10000L, 10000L,
                "测试买家", "13800138000", "测试省 测试市 测试路 1 号", trackingNo, expressCompany,
                null, now.plusMinutes(30), null, now, now, BUYER, BUYER);
    }
}
