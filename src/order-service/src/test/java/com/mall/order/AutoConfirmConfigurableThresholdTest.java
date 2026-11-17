package com.mall.order;

import com.mall.order.common.OrderStatus;
import com.mall.order.dto.request.ShipOrderRequest;
import com.mall.order.mapper.OrderMapper;
import com.mall.order.service.AutoConfirmJob;
import com.mall.order.service.OrderService;
import com.mall.order.service.PlatformConfigService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * 验收项「把 N 配成 1 分钟，造 SHIPPED 订单到期自动 COMPLETED」（任务 #138）。
 *
 * <p>单独一个测试类是因为需要把 {@link PlatformConfigService} 换成 mock：
 * 真实实现在测试环境连不上 Nacos，会回退到默认 10 天，
 * 那样就永远验证不到「可配置」这件事——只能验证「默认值能用」。
 *
 * <p>这里显式让配置返回 1 分钟，然后造一笔「1 分半钟前发货」的订单：
 * 定时任务扫一轮就应该把它改成 COMPLETED。这同时证明了阈值真的来自配置，
 * 而不是代码里写死的常量。
 */
@SpringBootTest
@ActiveProfiles("test")
class AutoConfirmConfigurableThresholdTest {

    private static final String BUYER = "U13820001";
    private static final String SELLER = "U13820002";
    private static final String ORDER_ID = "OLOG1MIN00001";

    @Autowired
    private OrderService orderService;

    @Autowired
    private AutoConfirmJob autoConfirmJob;

    @Autowired
    private OrderMapper orderMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @MockBean
    private PlatformConfigService platformConfigService;

    @BeforeEach
    void setUp() {
        // 自动确认阈值 = 1 分钟；其余键保持原默认，避免影响下单超时逻辑
        when(platformConfigService.currentLong("order.auto-confirm.minutes")).thenReturn(1L);
        when(platformConfigService.currentLong("order.timeout.minutes")).thenReturn(30L);

        jdbcTemplate.update("DELETE FROM order_logistics_track");
        jdbcTemplate.update("DELETE FROM order_compensation");
        jdbcTemplate.update("DELETE FROM order_idempotency");
        jdbcTemplate.update("DELETE FROM order_item");
        jdbcTemplate.update("DELETE FROM `order`");
    }

    @Test
    void oneMinuteThresholdCompletesShippedOrderThatIsAlreadyLate() {
        insertPaidOrder();
        orderService.ship(ORDER_ID, SELLER, "SELLER", shipRequest());

        // 1 分半钟前发货：超过 1 分钟阈值，应被判为到期
        jdbcTemplate.update("UPDATE `order` SET shipped_at = ? WHERE order_id = ?",
                LocalDateTime.now().minusSeconds(90), ORDER_ID);
        assertThat(orderMapper.selectById(ORDER_ID).getStatus()).isEqualTo(OrderStatus.SHIPPED);

        int confirmed = autoConfirmJob.scan();

        assertThat(confirmed).isEqualTo(1);
        assertThat(orderMapper.selectById(ORDER_ID).getStatus()).isEqualTo(OrderStatus.COMPLETED);
    }

    @Test
    void thirtySecondsAfterShipmentIsStillInsideTheOneMinuteWindow() {
        insertPaidOrder();
        orderService.ship(ORDER_ID, SELLER, "SELLER", shipRequest());

        // 30 秒前发货：还在 1 分钟窗口内，不能被提前确认
        jdbcTemplate.update("UPDATE `order` SET shipped_at = ? WHERE order_id = ?",
                LocalDateTime.now().minusSeconds(30), ORDER_ID);

        assertThat(autoConfirmJob.scan()).isZero();
        assertThat(orderMapper.selectById(ORDER_ID).getStatus()).isEqualTo(OrderStatus.SHIPPED);
    }

    @Test
    void remainingMinutesReflectsTheConfiguredOneMinute() {
        insertPaidOrder();
        orderService.ship(ORDER_ID, SELLER, "SELLER", shipRequest());
        jdbcTemplate.update("UPDATE `order` SET shipped_at = ? WHERE order_id = ?",
                LocalDateTime.now().minusSeconds(10), ORDER_ID);

        // 阈值 1 分钟、已过 10 秒 → 还剩 50 秒，向上取整为 1 分钟
        Long remaining = orderService.detail(ORDER_ID, BUYER, "BUYER").getAutoConfirmRemainingMinutes();
        assertThat(remaining).isEqualTo(1L);
    }

    private static ShipOrderRequest shipRequest() {
        ShipOrderRequest request = new ShipOrderRequest();
        request.setExpressCompany("JD");
        request.setTrackingNo("JD0001");
        return request;
    }

    private void insertPaidOrder() {
        LocalDateTime now = LocalDateTime.now();
        jdbcTemplate.update("INSERT INTO `order` (order_id, order_no, buyer_id, seller_id, status, "
                        + "total_amount, pay_amount, receiver, receiver_phone, address_full, tracking_no, "
                        + "express_company, shipped_at, expire_at, paid_at, created_at, updated_at, "
                        + "created_by, updated_by, is_deleted, inventory_released) "
                        + "VALUES (?, ?, ?, ?, 'PAID', ?, ?, ?, ?, ?, NULL, NULL, NULL, ?, NULL, ?, ?, ?, ?, 0, 0)",
                ORDER_ID, "NO" + ORDER_ID, BUYER, SELLER, 5000L, 5000L,
                "测试买家", "13800138000", "测试省 测试市 测试路 1 号",
                now.plusMinutes(30), now, now, BUYER, BUYER);
    }
}
