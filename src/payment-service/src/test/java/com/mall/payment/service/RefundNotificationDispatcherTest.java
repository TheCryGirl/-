package com.mall.payment.service;

import com.mall.payment.client.OrderClient;
import com.mall.payment.entity.RefundNotificationTask;
import com.mall.payment.client.ProductClient;
import com.mall.payment.client.dto.StockConfirmRequest;
import com.mall.payment.common.ErrorCode;
import com.mall.payment.common.Result;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 退款通知任务的持久调度（H2）。
 *
 * <p>覆盖：立即投递成功、业务失败后 attempts/next_retry_at 落库、退避封顶、
 * 只重放失败项（DONE 不重复远程调用）、条件 UPDATE 抢占、过期租约回收、
 * 「重启」后新建 dispatcher 仍能扫描到既有任务、异常文本不落库。
 */
@SpringBootTest
class RefundNotificationDispatcherTest {

    private static final String ORDER_ID = "O-NOTIFY-DISP";

    private static final String ORDER_REFUNDED = "ORDER_REFUNDED";

    private static final String STOCK_RESTOCK = "STOCK_RESTOCK";

    @Autowired
    private RefundNotificationDispatcher dispatcher;

    @Autowired
    private RefundNotificationTxService notificationTxService;

    @MockBean
    private OrderClient orderClient;

    @MockBean
    private ProductClient productClient;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void cleanDatabase() {
        jdbcTemplate.update("DELETE FROM refund_notification_task");
        stubDownstreamOk();
    }

    @Test
    void immediateDeliveryMarksBothTasksDone() {
        notificationTxService.createTasks(ORDER_ID);

        dispatcher.dispatchAfterCommit(ORDER_ID);

        verify(orderClient, times(1)).markRefunded(ORDER_ID);
        verify(productClient, times(1)).refundRestock(any(StockConfirmRequest.class));
        assertEquals("DONE", statusOf(ORDER_REFUNDED));
        assertEquals("DONE", statusOf(STOCK_RESTOCK));
        assertEquals(1, attemptsOf(ORDER_REFUNDED));
        assertEquals(1, attemptsOf(STOCK_RESTOCK));
        assertNull(lastErrorOf(ORDER_REFUNDED));
    }

    @Test
    void businessFailureKeepsTaskWithAttemptsAndNextRetry() {
        when(orderClient.markRefunded(ORDER_ID))
                .thenReturn(Result.fail(ErrorCode.SERVICE_UNAVAILABLE, "订单服务不可用"));
        notificationTxService.createTasks(ORDER_ID);

        dispatcher.dispatchAfterCommit(ORDER_ID);

        assertEquals("PENDING", statusOf(ORDER_REFUNDED));
        assertEquals(1, attemptsOf(ORDER_REFUNDED));
        assertNotNull(nextRetryAtOf(ORDER_REFUNDED), "失败后必须排下一次重试，不能只记日志");
        assertNotNull(lastErrorOf(ORDER_REFUNDED));
        // 另一个任务独立完成，不受影响
        assertEquals("DONE", statusOf(STOCK_RESTOCK));
        // 失败不留悬空租约
        assertNull(leaseUntilOf(ORDER_REFUNDED));
    }

    @Test
    void exceptionIsSanitizedAndDoesNotLoseTheTask() {
        when(orderClient.markRefunded(ORDER_ID))
                .thenThrow(new IllegalStateException("Authorization: Bearer secret-token-abc"));
        notificationTxService.createTasks(ORDER_ID);

        dispatcher.dispatchAfterCommit(ORDER_ID);

        String error = lastErrorOf(ORDER_REFUNDED);
        assertNotNull(error);
        assertFalse(error.contains("secret-token-abc"), "last_error 不能写入凭据");
        assertFalse(error.contains("IllegalStateException"), "last_error 不能写入异常类名/堆栈");
        assertEquals("PENDING", statusOf(ORDER_REFUNDED));
        assertEquals(1, attemptsOf(ORDER_REFUNDED));
    }

    @Test
    void scheduledScanOnlyReplaysFailedItem() {
        when(orderClient.markRefunded(ORDER_ID))
                .thenReturn(Result.fail(ErrorCode.SERVICE_UNAVAILABLE, "订单服务不可用"));
        notificationTxService.createTasks(ORDER_ID);
        dispatcher.dispatchAfterCommit(ORDER_ID);
        verify(orderClient, times(1)).markRefunded(ORDER_ID);
        verify(productClient, times(1)).refundRestock(any(StockConfirmRequest.class));

        // 到了重试时间，下游恢复
        stubDownstreamOk();
        makeDue(ORDER_REFUNDED);
        dispatcher.dispatchDue();

        verify(orderClient, times(2)).markRefunded(ORDER_ID);
        assertEquals("DONE", statusOf(ORDER_REFUNDED));
        // 已 DONE 的库存回补不再被调用
        verify(productClient, times(1)).refundRestock(any(StockConfirmRequest.class));
    }

    @Test
    void doneTasksAreNotCalledAgainByScheduledScan() {
        notificationTxService.createTasks(ORDER_ID);
        dispatcher.dispatchAfterCommit(ORDER_ID);

        makeDue(ORDER_REFUNDED);
        makeDue(STOCK_RESTOCK);
        dispatcher.dispatchDue();

        verify(orderClient, times(1)).markRefunded(ORDER_ID);
        verify(productClient, times(1)).refundRestock(any(StockConfirmRequest.class));
    }

    @Test
    void scheduledScanSkipsTasksWhoseRetryIsNotDue() {
        when(orderClient.markRefunded(ORDER_ID))
                .thenReturn(Result.fail(ErrorCode.SERVICE_UNAVAILABLE, "订单服务不可用"));
        notificationTxService.createTasks(ORDER_ID);
        dispatcher.dispatchAfterCommit(ORDER_ID);

        // next_retry_at 还在未来：调度扫描必须跳过
        dispatcher.dispatchDue();

        assertEquals(1, attemptsOf(ORDER_REFUNDED));
        verify(orderClient, times(1)).markRefunded(ORDER_ID);
    }

    @Test
    void newScheduledRunPicksUpExistingTasks() {
        notificationTxService.createTasks(ORDER_ID);
        makeAllDue();

        // 等价于「进程重启后另一个 dispatcher 实例扫描既有 PENDING 任务」
        new RefundNotificationDispatcher(notificationTxService, orderClient, productClient,
                30, 50, true).dispatchDue();

        verify(orderClient, times(1)).markRefunded(ORDER_ID);
        verify(productClient, times(1)).refundRestock(any(StockConfirmRequest.class));
        assertEquals("DONE", statusOf(ORDER_REFUNDED));
        assertEquals("DONE", statusOf(STOCK_RESTOCK));
    }

    @Test
    void liveLeaseCannotBeStolen() {
        notificationTxService.createTasks(ORDER_ID);
        setStatus(ORDER_REFUNDED, "PROCESSING",
                LocalDateTime.now().plusMinutes(5), null, 1);

        assertNull(notificationTxService.claim(
                ORDER_ID, ORDER_REFUNDED, LocalDateTime.now().plusMinutes(5)));
        assertEquals("PROCESSING", statusOf(ORDER_REFUNDED));

        dispatcher.dispatchDue();
        verify(orderClient, never()).markRefunded(ORDER_ID);
    }

    @Test
    void expiredLeaseIsReclaimedAndRetried() {
        notificationTxService.createTasks(ORDER_ID);
        setStatus(ORDER_REFUNDED, "PROCESSING", LocalDateTime.now().minusMinutes(5), null, 2);

        RefundNotificationTask claimed = notificationTxService.claim(
                ORDER_ID, ORDER_REFUNDED, LocalDateTime.now().plusSeconds(60));
        assertNotNull(claimed, "过期租约必须可以被回收");
        // attempts 记录的是累计尝试次数，回收时不重置；本次抢占再 +1
        assertEquals(3, claimed.getAttempts());
        assertTrue(claimed.getLeaseUntil().isAfter(LocalDateTime.now()));

        // 回收后由调度投递：把租约放到过去模拟又一次中断，确保到期可扫
        jdbcTemplate.update("UPDATE refund_notification_task SET status = 'PROCESSING', "
                        + "lease_until = ?, next_retry_at = ? WHERE order_id = ? AND event_type = ?",
                LocalDateTime.now().minusMinutes(1), LocalDateTime.now().minusSeconds(1),
                ORDER_ID, ORDER_REFUNDED);
        dispatcher.dispatchDue();
        verify(orderClient, times(1)).markRefunded(ORDER_ID);
        assertEquals("DONE", statusOf(ORDER_REFUNDED));
    }

    @Test
    void backoffGrowsExponentiallyAndIsCapped() {
        when(orderClient.markRefunded(ORDER_ID))
                .thenReturn(Result.fail(ErrorCode.SERVICE_UNAVAILABLE, "订单服务不可用"));
        notificationTxService.createTasks(ORDER_ID);

        dispatcher.dispatchAfterCommit(ORDER_ID);
        long first = nextRetryInSeconds(ORDER_REFUNDED);

        makeDue(ORDER_REFUNDED);
        dispatcher.dispatchDue();
        long second = nextRetryInSeconds(ORDER_REFUNDED);

        assertTrue(second > first, "退避间隔应递增：first=" + first + ", second=" + second);

        // 把重试次数推到上限附近，确认退避被固定封顶而不是无限增长
        setAttempts(ORDER_REFUNDED, 12);
        makeDue(ORDER_REFUNDED);
        when(orderClient.markRefunded(ORDER_ID)).thenThrow(new IllegalStateException("down"));
        dispatcher.dispatchDue();
        long capped = nextRetryInSeconds(ORDER_REFUNDED);
        assertTrue(capped <= 600, "退避必须封顶，实际=" + capped);

        // 反复失败不丢任务：仍是 PENDING，attempts 继续累加
        makeDue(ORDER_REFUNDED);
        dispatcher.dispatchDue();
        assertEquals("PENDING", statusOf(ORDER_REFUNDED));
        assertTrue(attemptsOf(ORDER_REFUNDED) >= 14);
    }

    private void stubDownstreamOk() {
        when(orderClient.markRefunded(ORDER_ID)).thenReturn(Result.success(null));
        when(productClient.refundRestock(any(StockConfirmRequest.class))).thenReturn(Result.success(null));
    }

    private void makeAllDue() {
        jdbcTemplate.update("UPDATE refund_notification_task SET next_retry_at = ? WHERE order_id = ?",
                LocalDateTime.now().minusSeconds(1), ORDER_ID);
    }

    private void makeDue(String eventType) {
        jdbcTemplate.update("UPDATE refund_notification_task SET next_retry_at = ? "
                        + "WHERE order_id = ? AND event_type = ?",
                LocalDateTime.now().minusSeconds(1), ORDER_ID, eventType);
    }

    private void setStatus(String eventType, String status, LocalDateTime leaseUntil,
                           LocalDateTime nextRetryAt, int attempts) {
        jdbcTemplate.update("UPDATE refund_notification_task SET status = ?, lease_until = ?, "
                        + "next_retry_at = COALESCE(?, next_retry_at), attempts = ? "
                        + "WHERE order_id = ? AND event_type = ?",
                status, leaseUntil, nextRetryAt, attempts, ORDER_ID, eventType);
    }

    private void setAttempts(String eventType, int attempts) {
        jdbcTemplate.update("UPDATE refund_notification_task SET attempts = ? "
                + "WHERE order_id = ? AND event_type = ?", attempts, ORDER_ID, eventType);
    }

    private String statusOf(String eventType) {
        return jdbcTemplate.queryForObject(
                "SELECT status FROM refund_notification_task WHERE order_id = ? AND event_type = ?",
                String.class, ORDER_ID, eventType);
    }

    private int attemptsOf(String eventType) {
        Integer value = jdbcTemplate.queryForObject(
                "SELECT attempts FROM refund_notification_task WHERE order_id = ? AND event_type = ?",
                Integer.class, ORDER_ID, eventType);
        return value == null ? 0 : value;
    }

    private String lastErrorOf(String eventType) {
        return jdbcTemplate.queryForObject(
                "SELECT last_error FROM refund_notification_task WHERE order_id = ? AND event_type = ?",
                String.class, ORDER_ID, eventType);
    }

    private Object leaseUntilOf(String eventType) {
        return jdbcTemplate.queryForObject(
                "SELECT lease_until FROM refund_notification_task WHERE order_id = ? AND event_type = ?",
                Object.class, ORDER_ID, eventType);
    }

    private Object nextRetryAtOf(String eventType) {
        return jdbcTemplate.queryForObject(
                "SELECT next_retry_at FROM refund_notification_task WHERE order_id = ? AND event_type = ?",
                Object.class, ORDER_ID, eventType);
    }

    private long nextRetryInSeconds(String eventType) {
        LocalDateTime nextRetry = jdbcTemplate.queryForObject(
                "SELECT next_retry_at FROM refund_notification_task WHERE order_id = ? AND event_type = ?",
                LocalDateTime.class, ORDER_ID, eventType);
        assertNotNull(nextRetry);
        return java.time.Duration.between(LocalDateTime.now(), nextRetry).getSeconds();
    }
}
