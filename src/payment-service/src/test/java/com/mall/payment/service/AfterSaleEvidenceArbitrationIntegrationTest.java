package com.mall.payment.service;

import com.mall.payment.client.OrderClient;
import com.mall.payment.client.ProductClient;
import com.mall.payment.client.dto.OrderDTO;
import com.mall.payment.client.dto.StockConfirmRequest;
import com.mall.payment.common.AfterSaleReasonCode;
import com.mall.payment.common.AfterSaleStatus;
import com.mall.payment.common.AfterSaleType;
import com.mall.payment.common.ArbitrationDecision;
import com.mall.payment.common.BizException;
import com.mall.payment.common.ErrorCode;
import com.mall.payment.common.OrderStatus;
import com.mall.payment.common.Result;
import com.mall.payment.dto.request.ArbitrateRequest;
import com.mall.payment.dto.request.CreateAfterSaleRequest;
import com.mall.payment.dto.request.SupplementRequest;
import com.mall.payment.dto.response.AfterSaleResponse;
import com.mall.payment.entity.AfterSaleEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * #143 售后举证与平台仲裁集成测试。
 *
 * <p>对应 Issue 验收：
 * 1) 上传 1-3 张并预览，超限被拒；2) 原因 OTHER 未填说明被拒；
 * 3) PENDING 可撤销、撤销后卖家不可审核、已通过不可撤销；
 * 4) 时间线正序、每条含操作人与备注；5) 卖家拒绝后可申请仲裁；
 * 6) 仲裁中卖家不能审核；7) 裁定退款后售后单与退款单状态一致；
 * 8) 非管理员裁定 10003、裁定理由为空被拒。另附并发裁定实测。
 */
@SpringBootTest(properties = "payment.upload-dir=${java.io.tmpdir}/mall-se-payment-uploads")
class AfterSaleEvidenceArbitrationIntegrationTest {

    private static final String ORDER_ID = "O70002";
    private static final String PAYMENT_ID = "PAYTEST0000000000008";
    private static final String PAY_NO = "P20261029000008";
    private static final String BUYER_ID = "U10002";
    private static final String SELLER_ID = "S10002";
    private static final String ADMIN_ID = "ADMIN01";

    @Autowired
    private AfterSaleService afterSaleService;

    @Autowired
    private AfterSaleAttachmentStorageService attachmentStorageService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Value("${payment.upload-dir}")
    private String uploadDir;

    @MockBean
    private OrderClient orderClient;

    @MockBean
    private ProductClient productClient;

    @BeforeEach
    void cleanDatabase() {
        jdbcTemplate.update("DELETE FROM after_sale_event");
        jdbcTemplate.update("DELETE FROM after_sale_attachment");
        jdbcTemplate.update("DELETE FROM refund_notification_task");
        jdbcTemplate.update("DELETE FROM after_sale_ticket");
        jdbcTemplate.update("DELETE FROM refund");
        jdbcTemplate.update("DELETE FROM payment_callback");
        jdbcTemplate.update("DELETE FROM payment");
        insertPaidPayment();
        stubOrder(OrderStatus.PAID);
        when(orderClient.markRefunded(ORDER_ID)).thenReturn(Result.success(null));
        when(productClient.refundRestock(any(StockConfirmRequest.class))).thenReturn(Result.success(null));
    }

    @Test
    void uploadValidatesCountSizeFormatAndReturnsPreviewUrls() throws Exception {
        List<String> urls = afterSaleService.uploadAttachments(BUYER_ID, List.of(
                png("a.png"), png("b.jpg"), png("c.webp")));
        assertEquals(3, urls.size());
        for (String url : urls) {
            assertTrue(url.matches("^/after-sale/[0-9a-f]{32}\\.(png|jpg|webp)$"), url);
            // 落盘成功，静态映射后可直接预览
            String filename = url.substring("/after-sale/".length());
            assertTrue(Files.exists(Paths.get(uploadDir, "after-sale", filename)), url);
        }
        // 只接受本服务生成的凭证路径，拒绝任意路径绑定
        attachmentStorageService.validateUrls(urls);
        BizException invalidPath = assertThrows(BizException.class,
                () -> attachmentStorageService.validateUrls(List.of("/etc/passwd")));
        assertEquals(ErrorCode.PARAM_INVALID, invalidPath.getCode());

        BizException tooMany = assertThrows(BizException.class, () -> afterSaleService.uploadAttachments(
                BUYER_ID, List.of(png("1.png"), png("2.png"), png("3.png"), png("4.png"))));
        assertEquals(ErrorCode.PARAM_INVALID, tooMany.getCode());

        byte[] big = new byte[5 * 1024 * 1024 + 1];
        BizException tooBig = assertThrows(BizException.class, () -> afterSaleService.uploadAttachments(
                BUYER_ID, List.of(new MockMultipartFile("files", "big.png", "image/png", big))));
        assertEquals(ErrorCode.PARAM_INVALID, tooBig.getCode());

        BizException badExt = assertThrows(BizException.class, () -> afterSaleService.uploadAttachments(
                BUYER_ID, List.of(new MockMultipartFile("files", "evil.txt", "text/plain", new byte[]{1}))));
        assertEquals(ErrorCode.PARAM_INVALID, badExt.getCode());
    }

    @Test
    void otherReasonCodeRequiresDescription() {
        BizException missing = assertThrows(BizException.class,
                () -> create("other-blank-1", AfterSaleType.REFUND_ONLY, 1000L,
                        AfterSaleReasonCode.OTHER, "  ", null));
        assertEquals(ErrorCode.PARAM_INVALID, missing.getCode());

        AfterSaleResponse ticket = create("other-ok-1", AfterSaleType.REFUND_ONLY, 1000L,
                AfterSaleReasonCode.OTHER, "不想要了", null);
        assertEquals(AfterSaleReasonCode.OTHER, ticket.getReasonCode());
        assertEquals("不想要了", ticket.getReason());

        List<String> four = List.of(
                "/after-sale/" + "a".repeat(32) + ".png",
                "/after-sale/" + "b".repeat(32) + ".png",
                "/after-sale/" + "c".repeat(32) + ".png",
                "/after-sale/" + "d".repeat(32) + ".png");
        BizException tooMany = assertThrows(BizException.class,
                () -> create("too-many-1", AfterSaleType.REFUND_ONLY, 1000L, null, "凭证超限", four));
        assertEquals(ErrorCode.PARAM_INVALID, tooMany.getCode());

        BizException invalidUrl = assertThrows(BizException.class,
                () -> create("bad-url-1", AfterSaleType.REFUND_ONLY, 1000L, null, "路径非法",
                        List.of("/etc/passwd")));
        assertEquals(ErrorCode.PARAM_INVALID, invalidUrl.getCode());
    }

    @Test
    void pendingCanBeCancelledAndCancelledBlocksAudit() {
        AfterSaleResponse ticket = create("cancel-1", AfterSaleType.REFUND_ONLY, 1000L);

        BizException wrongBuyer = assertThrows(BizException.class,
                () -> afterSaleService.cancel(ticket.getTicketId(), "U20002", "越权撤销"));
        assertEquals(ErrorCode.FORBIDDEN, wrongBuyer.getCode());

        AfterSaleResponse cancelled = afterSaleService.cancel(
                ticket.getTicketId(), BUYER_ID, "不想要了");
        assertEquals(AfterSaleStatus.CANCELLED, cancelled.getStatus());
        assertEquals(AfterSaleStatus.CANCELLED,
                afterSaleService.cancel(ticket.getTicketId(), BUYER_ID, "重复撤销").getStatus());

        BizException auditBlocked = assertThrows(BizException.class,
                () -> afterSaleService.audit(ticket.getTicketId(), SELLER_ID, "SELLER", true, "同意"));
        assertEquals(ErrorCode.AFTER_SALE_STATE_INVALID, auditBlocked.getCode());

        // 已通过后不可撤销
        AfterSaleResponse approvedTicket = create("cancel-2", AfterSaleType.REFUND_ONLY, 1000L);
        afterSaleService.audit(approvedTicket.getTicketId(), SELLER_ID, "SELLER", true, "同意");
        BizException approvedBlocked = assertThrows(BizException.class,
                () -> afterSaleService.cancel(approvedTicket.getTicketId(), BUYER_ID, "想撤销"));
        assertEquals(ErrorCode.AFTER_SALE_STATE_INVALID, approvedBlocked.getCode());

        // 撤销释放 active_flag，同一订单可以重新申请售后
        AfterSaleResponse recreated = create("cancel-3", AfterSaleType.REFUND_ONLY, 1000L);
        assertEquals(AfterSaleStatus.PENDING, recreated.getStatus());
    }

    @Test
    void timelineRecordsOperatorsRemarksAndEvidence() {
        List<String> urls = afterSaleService.uploadAttachments(BUYER_ID, List.of(png("e1.png"), png("e2.png")));
        AfterSaleResponse ticket = create("timeline-1", AfterSaleType.REFUND_ONLY, 5000L,
                AfterSaleReasonCode.QUALITY, "屏幕有坏点", urls);

        AfterSaleResponse detail = afterSaleService.detail(BUYER_ID, "BUYER", ticket.getTicketId());
        assertNotNull(detail.getEvents());
        assertEquals(1, detail.getEvents().size());
        AfterSaleEvent created = detail.getEvents().get(0);
        assertNull(created.getFromStatus());
        assertEquals(AfterSaleStatus.PENDING, created.getToStatus());
        assertEquals(BUYER_ID, created.getOperatorId());
        assertEquals("屏幕有坏点", created.getRemark());
        assertNotNull(detail.getAttachments());
        assertEquals(2, detail.getAttachments().size());
        assertEquals(urls, detail.getAttachments().stream().map(a -> a.getUrl()).toList());

        // 列表为轻量结构，不逐行查时间线与凭证
        AfterSaleResponse listItem = afterSaleService
                .list(BUYER_ID, "BUYER", null, null, null, 1, 20).getList().get(0);
        assertNull(listItem.getEvents());
        assertNull(listItem.getAttachments());

        afterSaleService.audit(ticket.getTicketId(), SELLER_ID, "SELLER", false, "商家驳回：不影响使用");
        detail = afterSaleService.detail(BUYER_ID, "BUYER", ticket.getTicketId());
        assertEquals(2, detail.getEvents().size());
        AfterSaleEvent rejected = detail.getEvents().get(1);
        assertEquals(AfterSaleStatus.PENDING, rejected.getFromStatus());
        assertEquals(AfterSaleStatus.REJECTED, rejected.getToStatus());
        assertEquals(SELLER_ID, rejected.getOperatorId());
        assertEquals("商家驳回：不影响使用", rejected.getRemark());

        afterSaleService.requestArbitration(ticket.getTicketId(), BUYER_ID, "申请平台仲裁");
        detail = afterSaleService.detail(BUYER_ID, "BUYER", ticket.getTicketId());
        assertEquals(3, detail.getEvents().size());
        AfterSaleEvent arbitrating = detail.getEvents().get(2);
        assertEquals(AfterSaleStatus.REJECTED, arbitrating.getFromStatus());
        assertEquals(AfterSaleStatus.ARBITRATING, arbitrating.getToStatus());
        assertEquals(BUYER_ID, arbitrating.getOperatorId());

        // ARBITRATING 过滤查询（Issue 验收 GET /api/after-sales?status=ARBITRATING）
        assertEquals(1, afterSaleService.list(BUYER_ID, "BUYER", "ARBITRATING", null, null, 1, 20).getTotal());
        assertEquals(1, afterSaleService.list(ADMIN_ID, "ADMIN", "ARBITRATING", null, null, 1, 20).getTotal());

        // 材料与时间线对双方可见
        AfterSaleResponse sellerDetail = afterSaleService.detail(SELLER_ID, "SELLER", ticket.getTicketId());
        assertEquals(3, sellerDetail.getEvents().size());
        assertEquals(2, sellerDetail.getAttachments().size());

        // 仲裁期间卖家不能审核
        BizException locked = assertThrows(BizException.class,
                () -> afterSaleService.audit(ticket.getTicketId(), SELLER_ID, "SELLER", true, "想再审核"));
        assertEquals(ErrorCode.AFTER_SALE_STATE_INVALID, locked.getCode());
    }

    @Test
    void arbitrateRefundSyncsTicketWithRefund() {
        AfterSaleResponse ticket = create("arb-refund-1", AfterSaleType.REFUND_ONLY, 5000L);
        afterSaleService.audit(ticket.getTicketId(), SELLER_ID, "SELLER", false, "卖家驳回");
        afterSaleService.requestArbitration(ticket.getTicketId(), BUYER_ID, "申请仲裁");

        AfterSaleResponse ruled = afterSaleService.arbitrate(ticket.getTicketId(), ADMIN_ID, "ADMIN",
                arbitrate(ArbitrationDecision.REFUND, "平台裁定支持退款"));
        assertEquals(AfterSaleStatus.REFUNDED, ruled.getStatus());
        assertNotNull(ruled.getRefundId());
        assertEquals("SUCCESS", refundStatus());
        assertEquals(5000L, refundedAmount());
        assertEquals("REFUNDED", ticketStatus(ticket.getTicketId()));
        assertEquals(1, count("refund"));

        List<AfterSaleEvent> events = afterSaleService.detail(BUYER_ID, "BUYER", ticket.getTicketId()).getEvents();
        AfterSaleEvent last = events.get(events.size() - 1);
        assertEquals(AfterSaleStatus.ARBITRATING, last.getFromStatus());
        assertEquals(AfterSaleStatus.REFUNDED, last.getToStatus());
        assertEquals(ADMIN_ID, last.getOperatorId());
        assertEquals("平台裁定支持退款", last.getRemark());

        // 裁定后状态已结束，不能重复裁定
        BizException again = assertThrows(BizException.class,
                () -> afterSaleService.arbitrate(ticket.getTicketId(), ADMIN_ID, "ADMIN",
                        arbitrate(ArbitrationDecision.REFUND, "重复裁定")));
        assertEquals(ErrorCode.AFTER_SALE_STATE_INVALID, again.getCode());
        assertEquals(1, count("refund"));
    }

    @Test
    void arbitrateRejectEndsArbitrationWithoutRefund() {
        AfterSaleResponse ticket = create("arb-reject-1", AfterSaleType.REFUND_ONLY, 5000L);
        afterSaleService.audit(ticket.getTicketId(), SELLER_ID, "SELLER", false, "卖家驳回");
        afterSaleService.requestArbitration(ticket.getTicketId(), BUYER_ID, "申请仲裁");

        AfterSaleResponse ruled = afterSaleService.arbitrate(ticket.getTicketId(), ADMIN_ID, "ADMIN",
                arbitrate(ArbitrationDecision.REJECT, "平台裁定维持驳回"));
        assertEquals(AfterSaleStatus.REJECTED, ruled.getStatus());
        assertEquals(0, count("refund"));
        assertEquals(0L, refundedAmount());

        List<AfterSaleEvent> events = afterSaleService.detail(BUYER_ID, "BUYER", ticket.getTicketId()).getEvents();
        AfterSaleEvent last = events.get(events.size() - 1);
        assertEquals(AfterSaleStatus.ARBITRATING, last.getFromStatus());
        assertEquals(AfterSaleStatus.REJECTED, last.getToStatus());
        assertEquals(ADMIN_ID, last.getOperatorId());
    }

    @Test
    void arbitrateRequiresAdminAndRemark() {
        AfterSaleResponse ticket = create("arb-guard-1", AfterSaleType.REFUND_ONLY, 5000L);

        // 非 ARBITRATING 状态不能裁定
        BizException notArbitrating = assertThrows(BizException.class,
                () -> afterSaleService.arbitrate(ticket.getTicketId(), ADMIN_ID, "ADMIN",
                        arbitrate(ArbitrationDecision.REJECT, "越权状态裁定")));
        assertEquals(ErrorCode.AFTER_SALE_STATE_INVALID, notArbitrating.getCode());

        afterSaleService.audit(ticket.getTicketId(), SELLER_ID, "SELLER", false, "卖家驳回");
        afterSaleService.requestArbitration(ticket.getTicketId(), BUYER_ID, "申请仲裁");

        // 非管理员调用裁定返回 10003
        BizException forbidden = assertThrows(BizException.class,
                () -> afterSaleService.arbitrate(ticket.getTicketId(), SELLER_ID, "SELLER",
                        arbitrate(ArbitrationDecision.REFUND, "卖家想自己裁定")));
        assertEquals(ErrorCode.FORBIDDEN, forbidden.getCode());

        BizException blankRemark = assertThrows(BizException.class,
                () -> afterSaleService.arbitrate(ticket.getTicketId(), ADMIN_ID, "ADMIN",
                        arbitrate(ArbitrationDecision.REFUND, "  ")));
        assertEquals(ErrorCode.PARAM_INVALID, blankRemark.getCode());

        ArbitrateRequest nullDecision = new ArbitrateRequest();
        nullDecision.setRemark("有理由");
        BizException missingDecision = assertThrows(BizException.class,
                () -> afterSaleService.arbitrate(ticket.getTicketId(), ADMIN_ID, "ADMIN", nullDecision));
        assertEquals(ErrorCode.PARAM_INVALID, missingDecision.getCode());
    }

    @Test
    void exchangeArbitrationCannotRefund() {
        AfterSaleResponse ticket = create("arb-exchange-1", AfterSaleType.EXCHANGE, null);
        afterSaleService.audit(ticket.getTicketId(), SELLER_ID, "SELLER", false, "卖家驳回换货");
        afterSaleService.requestArbitration(ticket.getTicketId(), BUYER_ID, "申请仲裁");

        BizException noRefund = assertThrows(BizException.class,
                () -> afterSaleService.arbitrate(ticket.getTicketId(), ADMIN_ID, "ADMIN",
                        arbitrate(ArbitrationDecision.REFUND, "想退款")));
        assertEquals(ErrorCode.PARAM_INVALID, noRefund.getCode());

        AfterSaleResponse ruled = afterSaleService.arbitrate(ticket.getTicketId(), ADMIN_ID, "ADMIN",
                arbitrate(ArbitrationDecision.REJECT, "维持驳回"));
        assertEquals(AfterSaleStatus.REJECTED, ruled.getStatus());
    }

    @Test
    void supplementRequiresArbitratingAndContent() {
        AfterSaleResponse ticket = create("supp-1", AfterSaleType.REFUND_ONLY, 5000L);
        SupplementRequest empty = new SupplementRequest();
        BizException notArbitrating = assertThrows(BizException.class,
                () -> afterSaleService.supplement(ticket.getTicketId(), BUYER_ID, "BUYER", empty));
        assertEquals(ErrorCode.AFTER_SALE_STATE_INVALID, notArbitrating.getCode());

        afterSaleService.audit(ticket.getTicketId(), SELLER_ID, "SELLER", false, "卖家驳回");
        afterSaleService.requestArbitration(ticket.getTicketId(), BUYER_ID, "申请仲裁");

        BizException blank = assertThrows(BizException.class,
                () -> afterSaleService.supplement(ticket.getTicketId(), BUYER_ID, "BUYER", empty));
        assertEquals(ErrorCode.PARAM_INVALID, blank.getCode());

        // 卖家补充说明，买家可见
        SupplementRequest sellerRemark = new SupplementRequest();
        sellerRemark.setRemark("买家举证不实，附聊天记录");
        afterSaleService.supplement(ticket.getTicketId(), SELLER_ID, "SELLER", sellerRemark);

        // 买家补充凭证，卖家可见
        List<String> urls = afterSaleService.uploadAttachments(BUYER_ID, List.of(png("s1.png")));
        SupplementRequest buyerEvidence = new SupplementRequest();
        buyerEvidence.setAttachmentUrls(urls);
        afterSaleService.supplement(ticket.getTicketId(), BUYER_ID, "BUYER", buyerEvidence);

        AfterSaleResponse buyerDetail = afterSaleService.detail(BUYER_ID, "BUYER", ticket.getTicketId());
        assertEquals(5, buyerDetail.getEvents().size());
        assertEquals(1, buyerDetail.getAttachments().size());
        assertEquals(urls.get(0), buyerDetail.getAttachments().get(0).getUrl());
        AfterSaleEvent sellerEvent = buyerDetail.getEvents().get(3);
        assertEquals(AfterSaleStatus.ARBITRATING, sellerEvent.getFromStatus());
        assertEquals(AfterSaleStatus.ARBITRATING, sellerEvent.getToStatus());
        assertEquals(SELLER_ID, sellerEvent.getOperatorId());
        assertEquals("买家举证不实，附聊天记录", sellerEvent.getRemark());

        AfterSaleResponse sellerDetail = afterSaleService.detail(SELLER_ID, "SELLER", ticket.getTicketId());
        assertEquals(5, sellerDetail.getEvents().size());
        assertEquals(1, sellerDetail.getAttachments().size());

        // 越权补充被拒
        BizException otherBuyer = assertThrows(BizException.class,
                () -> afterSaleService.supplement(ticket.getTicketId(), "U20002", "BUYER", sellerRemark));
        assertEquals(ErrorCode.FORBIDDEN, otherBuyer.getCode());
    }

    @Test
    void concurrentArbitrateRefundCreatesOnlyOneRefund() throws Exception {
        AfterSaleResponse ticket = create("arb-concurrent-1", AfterSaleType.REFUND_ONLY, 5000L);
        afterSaleService.audit(ticket.getTicketId(), SELLER_ID, "SELLER", false, "卖家驳回");
        afterSaleService.requestArbitration(ticket.getTicketId(), BUYER_ID, "申请仲裁");

        int threads = 8;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch ready = new CountDownLatch(threads);
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger success = new AtomicInteger();
        AtomicInteger failure = new AtomicInteger();
        List<Future<?>> futures = new ArrayList<>();
        for (int i = 0; i < threads; i++) {
            futures.add(pool.submit(() -> {
                ready.countDown();
                try {
                    start.await();
                    afterSaleService.arbitrate(ticket.getTicketId(), ADMIN_ID, "ADMIN",
                            arbitrate(ArbitrationDecision.REFUND, "并发裁定退款"));
                    success.incrementAndGet();
                } catch (BizException e) {
                    // 并发失败方必须回滚本事务创建的退款与额度预占
                    failure.incrementAndGet();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    failure.incrementAndGet();
                }
            }));
        }
        ready.await();
        start.countDown();
        for (Future<?> future : futures) {
            future.get(60, TimeUnit.SECONDS);
        }
        pool.shutdown();

        assertEquals(1, success.get(), "8 线程并发裁定只有一次成功");
        assertEquals(threads - 1, failure.get(), "其余 7 次以状态冲突失败并回滚");
        assertEquals(1, count("refund"), "只产生 1 笔退款");
        assertEquals(5000L, refundedAmount(), "退款预占额度只有一笔");
        assertEquals("REFUNDED", ticketStatus(ticket.getTicketId()));
        assertEquals("SUCCESS", refundStatus());
    }

    private AfterSaleResponse create(String key, AfterSaleType type, Long amount,
                                     AfterSaleReasonCode reasonCode, String reason,
                                     List<String> attachmentUrls) {
        CreateAfterSaleRequest request = new CreateAfterSaleRequest();
        request.setOrderId(ORDER_ID);
        request.setType(type);
        request.setAmount(amount);
        request.setReasonCode(reasonCode);
        request.setReason(reason);
        request.setAttachmentUrls(attachmentUrls);
        return afterSaleService.create(BUYER_ID, key, request);
    }

    private AfterSaleResponse create(String key, AfterSaleType type, Long amount) {
        return create(key, type, amount, null, "集成测试售后", null);
    }

    private ArbitrateRequest arbitrate(ArbitrationDecision decision, String remark) {
        ArbitrateRequest request = new ArbitrateRequest();
        request.setDecision(decision);
        request.setRemark(remark);
        return request;
    }

    private MultipartFile png(String name) {
        return new MockMultipartFile("files", name, "image/png", new byte[]{1, 2, 3});
    }

    private void stubOrder(OrderStatus status) {
        OrderDTO order = new OrderDTO();
        order.setOrderId(ORDER_ID);
        order.setBuyerId(BUYER_ID);
        order.setSellerId(SELLER_ID);
        order.setStatus(status);
        order.setPayAmount(11800L);
        when(orderClient.getOrder(ORDER_ID, BUYER_ID)).thenReturn(Result.success(order));
    }

    private void insertPaidPayment() {
        jdbcTemplate.update("""
                INSERT INTO payment (
                    payment_id, pay_no, order_id, buyer_id, seller_id, channel, amount,
                    refunded_amount, status, paid_at, created_by, updated_by, is_deleted
                ) VALUES (?, ?, ?, ?, ?, 'MOCK', 11800, 0, 'SUCCESS', CURRENT_TIMESTAMP, ?, ?, 0)
                """, PAYMENT_ID, PAY_NO, ORDER_ID, BUYER_ID, SELLER_ID, BUYER_ID, BUYER_ID);
    }

    private String ticketStatus(String ticketId) {
        return jdbcTemplate.queryForObject(
                "SELECT status FROM after_sale_ticket WHERE ticket_id = ?", String.class, ticketId);
    }

    private long refundedAmount() {
        Long value = jdbcTemplate.queryForObject(
                "SELECT refunded_amount FROM payment WHERE payment_id = ?", Long.class, PAYMENT_ID);
        return value == null ? 0 : value;
    }

    private String refundStatus() {
        return jdbcTemplate.queryForObject(
                "SELECT status FROM refund WHERE payment_id = ?", String.class, PAYMENT_ID);
    }

    private int count(String table) {
        Integer count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM " + table, Integer.class);
        return count == null ? 0 : count;
    }
}