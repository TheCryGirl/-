package com.mall.payment.service;

import com.mall.payment.entity.RefundNotificationTask;
import com.mall.payment.mapper.RefundNotificationTaskMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 退款通知任务的事务语义（H2）。
 *
 * <p>核心约束：dispatch 不持有 DB 事务；claim / 完成 / 失败持久化必须在
 * REQUIRES_NEW 的独立事务里执行。若这些方法不带 REQUIRES_NEW，被业务事务或
 * afterCommit 回调（线程仍绑定原事务资源）调用时就会把写操作挂到别人的事务上，
 * 因此断言独立提交结果在外层回滚后仍保留，不使用错误的“REQUIRES_NEW必须抛异常”假设。
 */
@SpringBootTest
class RefundNotificationTxServiceTest {

    private static final String ORDER_ID = "O-NOTIFY-TX";

    @Autowired
    private RefundNotificationTxService notificationTxService;

    @Autowired
    private RefundNotificationTaskMapper taskMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @BeforeEach
    void cleanDatabase() {
        jdbcTemplate.update("DELETE FROM refund_notification_task");
    }

    @Test
    void createTasksWritesTwoRowsInOneTransaction() {
        int created = notificationTxService.createTasks(ORDER_ID);

        assertEquals(2, created);
        assertEquals(2, count());
        assertTrue(exists(ORDER_ID, "ORDER_REFUNDED"));
        assertTrue(exists(ORDER_ID, "STOCK_RESTOCK"));
        assertEquals("PENDING", statusOf(ORDER_ID, "ORDER_REFUNDED"));
        assertEquals(0, attemptsOf(ORDER_ID, "ORDER_REFUNDED"));
        org.junit.jupiter.api.Assertions.assertNull(jdbcTemplate.queryForObject(
                "SELECT next_retry_at FROM refund_notification_task WHERE order_id=? AND event_type='ORDER_REFUNDED'",
                Object.class, ORDER_ID), "新任务必须立即可投递，不受数据库时间舍入影响");
    }

    @Test
    void createTasksIsIdempotentForSameOrder() {
        notificationTxService.createTasks(ORDER_ID);
        notificationTxService.createTasks(ORDER_ID);

        assertEquals(2, count());
    }

    @Test
    void claimPersistsProcessingInItsOwnTransaction() {
        notificationTxService.createTasks(ORDER_ID);

        RefundNotificationTask claimed = notificationTxService.claim(
                ORDER_ID, "ORDER_REFUNDED", LocalDateTime.now().plusSeconds(60));

        assertNotNull(claimed);
        assertEquals("PROCESSING", statusOf(ORDER_ID, "ORDER_REFUNDED"));
        assertNotNull(leaseUntilOf(ORDER_ID, "ORDER_REFUNDED"));
    }

    @Test
    void claimCommitsEvenWhenCallerRollsBack() {
        notificationTxService.createTasks(ORDER_ID);
        transactionTemplate.executeWithoutResult(status -> {
            assertNotNull(notificationTxService.claim(ORDER_ID, "ORDER_REFUNDED", LocalDateTime.now().plusSeconds(60)));
            status.setRollbackOnly();
        });
        assertEquals("PROCESSING", statusOf(ORDER_ID, "ORDER_REFUNDED"));
        assertEquals(1, attemptsOf(ORDER_ID, "ORDER_REFUNDED"));
    }

    @Test
    void completionAndRetryCommitIndependentlyOfCallerRollback() {
        notificationTxService.createTasks(ORDER_ID);
        var done = notificationTxService.claim(ORDER_ID, "ORDER_REFUNDED", LocalDateTime.now().plusSeconds(60));
        var failed = notificationTxService.claim(ORDER_ID, "STOCK_RESTOCK", LocalDateTime.now().plusSeconds(60));
        transactionTemplate.executeWithoutResult(status -> {
            assertEquals(1, notificationTxService.markDone(done));
            assertEquals(1, notificationTxService.markFailed(failed,"Downstream call failed",LocalDateTime.now().plusSeconds(60)));
            status.setRollbackOnly();
        });
        assertEquals("DONE",statusOf(ORDER_ID,"ORDER_REFUNDED"));
        assertEquals("PENDING",statusOf(ORDER_ID,"STOCK_RESTOCK"));
    }

    @Test
    void createdTasksRollBackWithBusinessTransaction() {
        transactionTemplate.executeWithoutResult(status -> {
            notificationTxService.createTasks(ORDER_ID);
            status.setRollbackOnly();
        });
        assertEquals(0,count());
    }

    @Test
    void staleWorkerCannotCompleteOrRescheduleNewLeaseGeneration() {
        notificationTxService.createTasks(ORDER_ID);
        var old = notificationTxService.claim(ORDER_ID,"ORDER_REFUNDED",LocalDateTime.now().plusSeconds(60));
        jdbcTemplate.update("UPDATE refund_notification_task SET lease_until=? WHERE event_id=?",LocalDateTime.now().minusMinutes(1),old.getEventId());
        var current = notificationTxService.claim(ORDER_ID,"ORDER_REFUNDED",LocalDateTime.now().plusSeconds(60));
        assertNotNull(current);
        assertEquals(0,notificationTxService.markDone(old));
        assertEquals(0,notificationTxService.markFailed(old,"stale",LocalDateTime.now()));
        assertEquals(1,notificationTxService.markDone(current));
        assertEquals("DONE",statusOf(ORDER_ID,"ORDER_REFUNDED"));
    }

    @Test
    void upsertDoesNotOverwriteDoneTask() {
        notificationTxService.createTasks(ORDER_ID);
        RefundNotificationTask claimed = notificationTxService.claim(
                ORDER_ID, "ORDER_REFUNDED", LocalDateTime.now().plusSeconds(60));
        assertNotNull(claimed, "新任务应立即可抢占");
        notificationTxService.markDone(claimed);
        assertEquals("DONE", statusOf(ORDER_ID, "ORDER_REFUNDED"));

        notificationTxService.createTasks(ORDER_ID);

        assertEquals("DONE", statusOf(ORDER_ID, "ORDER_REFUNDED"));
        assertEquals(2, count());
    }

    @Test
    void overduePendingTaskIsClaimable() {
        notificationTxService.createTasks(ORDER_ID);
        RefundNotificationTask task = taskMapper.selectByOrderId(ORDER_ID).stream()
                .filter(item -> "ORDER_REFUNDED".equals(item.getEventType()))
                .findFirst()
                .orElseThrow();
        jdbcTemplate.update("UPDATE refund_notification_task SET next_retry_at = ? WHERE event_id = ?",
                LocalDateTime.now().minusMinutes(1), task.getEventId());

        RefundNotificationTask claimed = notificationTxService.claim(
                ORDER_ID, "ORDER_REFUNDED", LocalDateTime.now().plusSeconds(60));

        assertNotNull(claimed);
        assertEquals("PROCESSING", statusOf(ORDER_ID, "ORDER_REFUNDED"));
    }

    private int count() {
        Integer value = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM refund_notification_task", Integer.class);
        return value == null ? 0 : value;
    }

    private boolean exists(String orderId, String eventType) {
        Integer value = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM refund_notification_task WHERE order_id = ? AND event_type = ?",
                Integer.class, orderId, eventType);
        return value != null && value > 0;
    }

    private String statusOf(String orderId, String eventType) {
        return jdbcTemplate.queryForObject(
                "SELECT status FROM refund_notification_task WHERE order_id = ? AND event_type = ?",
                String.class, orderId, eventType);
    }

    private int attemptsOf(String orderId, String eventType) {
        Integer value = jdbcTemplate.queryForObject(
                "SELECT attempts FROM refund_notification_task WHERE order_id = ? AND event_type = ?",
                Integer.class, orderId, eventType);
        return value == null ? 0 : value;
    }

    private Object leaseUntilOf(String orderId, String eventType) {
        return jdbcTemplate.queryForObject(
                "SELECT lease_until FROM refund_notification_task WHERE order_id = ? AND event_type = ?",
                Object.class, orderId, eventType);
    }
}
