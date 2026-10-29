package com.mall.order;

import com.mall.order.common.BizException;
import com.mall.order.common.ErrorCode;
import com.mall.order.dto.request.CreateEvaluationRequest;
import com.mall.order.dto.response.EvaluationResponse;
import com.mall.order.dto.response.EvaluationStatsResponse;
import com.mall.order.service.EvaluationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** 订单评价与晒图（任务 #57）的集成测试。 */
@SpringBootTest
@ActiveProfiles("test")
class EvaluationServiceIntegrationTest {

    private static final String BUYER = "U20001";
    private static final String OTHER = "U20002";
    private static final String SELLER = "U20003";
    private static final String ADMIN = "U20009";
    private static final String ORDER_ID = "OEVAL000001";
    private static final String PRODUCT_ID = "PEVAL0001";

    @Autowired
    private EvaluationService evaluationService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() {
        jdbcTemplate.update("DELETE FROM audit_log");
        jdbcTemplate.update("DELETE FROM order_evaluation");
        jdbcTemplate.update("DELETE FROM order_compensation");
        jdbcTemplate.update("DELETE FROM order_idempotency");
        jdbcTemplate.update("DELETE FROM order_item");
        jdbcTemplate.update("DELETE FROM `order`");
    }

    @Test
    void completedOrderCanBeEvaluatedOnceAndShowsInProductStats() {
        insertOrder(ORDER_ID, BUYER, SELLER, "COMPLETED");
        insertItem(ORDER_ID, PRODUCT_ID, "SEVAL0001");

        EvaluationResponse created = evaluationService.create(BUYER, ORDER_ID, evaluation(5, "很好用", true));
        assertThat(created.getEvaluationId()).startsWith("E");
        assertThat(created.getRating()).isEqualTo(5);
        assertThat(created.getImages()).hasSize(1);
        assertThat(created.getAnonymous()).isTrue();
        assertThat(created.getBuyerId()).isNull();
        assertThat(created.getBuyerLabel()).isEqualTo("匿名用户");

        EvaluationStatsResponse stats = evaluationService.stats(PRODUCT_ID);
        assertThat(stats.getTotal()).isEqualTo(1);
        assertThat(stats.getAverage()).isEqualTo(5.0);

        assertThat(evaluationService.listByProduct(PRODUCT_ID, 1, 10).list()).hasSize(1);
        assertThat(evaluationService.listByOrder(ORDER_ID, SELLER, "SELLER")).hasSize(1);

        assertThatThrownBy(() -> evaluationService.create(BUYER, ORDER_ID, evaluation(4, "再评一次", false)))
                .isInstanceOf(BizException.class)
                .extracting(e -> ((BizException) e).getCode())
                .isEqualTo(ErrorCode.EVALUATION_EXISTS);
    }

    @Test
    void onlyBuyerOfCompletedOrderCanEvaluate() {
        insertOrder(ORDER_ID, BUYER, SELLER, "COMPLETED");
        insertItem(ORDER_ID, PRODUCT_ID, "SEVAL0001");

        assertThatThrownBy(() -> evaluationService.create(OTHER, ORDER_ID, evaluation(5, "不是我的订单", false)))
                .isInstanceOf(BizException.class)
                .extracting(e -> ((BizException) e).getCode())
                .isEqualTo(ErrorCode.FORBIDDEN);

        String pendingOrder = "OEVAL000002";
        insertOrder(pendingOrder, BUYER, SELLER, "PENDING_PAYMENT");
        insertItem(pendingOrder, PRODUCT_ID, "SEVAL0002");
        assertThatThrownBy(() -> evaluationService.create(BUYER, pendingOrder, evaluation(5, "还没收货", false)))
                .isInstanceOf(BizException.class)
                .extracting(e -> ((BizException) e).getCode())
                .isEqualTo(ErrorCode.ORDER_STATE_INVALID);
    }

    @Test
    void blankContentWithoutImageIsRejected() {
        insertOrder(ORDER_ID, BUYER, SELLER, "COMPLETED");
        insertItem(ORDER_ID, PRODUCT_ID, "SEVAL0001");

        CreateEvaluationRequest request = new CreateEvaluationRequest();
        request.setRating(4);
        assertThatThrownBy(() -> evaluationService.create(BUYER, ORDER_ID, request))
                .isInstanceOf(BizException.class)
                .extracting(e -> ((BizException) e).getCode())
                .isEqualTo(ErrorCode.PARAM_INVALID);
    }

    @Test
    void adminCanHideAndRestoreEvaluationAndActionIsAudited() {
        insertOrder(ORDER_ID, BUYER, SELLER, "COMPLETED");
        insertItem(ORDER_ID, PRODUCT_ID, "SEVAL0001");
        EvaluationResponse created = evaluationService.create(BUYER, ORDER_ID, evaluation(2, "与描述不符", false));

        assertThatThrownBy(() -> evaluationService.hide(created.getEvaluationId(), BUYER, "BUYER"))
                .isInstanceOf(BizException.class)
                .extracting(e -> ((BizException) e).getCode())
                .isEqualTo(ErrorCode.FORBIDDEN);

        evaluationService.hide(created.getEvaluationId(), ADMIN, "ADMIN");
        assertThat(evaluationService.listByProduct(PRODUCT_ID, 1, 10).list()).isEmpty();
        assertThat(evaluationService.stats(PRODUCT_ID).getTotal()).isZero();
        assertThat(evaluationService.adminList("HIDDEN", PRODUCT_ID, 1, 10).list()).hasSize(1);

        Integer auditCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM audit_log WHERE action = 'EVALUATION_HIDE' AND target_id = ?",
                Integer.class, created.getEvaluationId());
        assertThat(auditCount).isEqualTo(1);

        evaluationService.show(created.getEvaluationId(), ADMIN, "ADMIN");
        assertThat(evaluationService.listByProduct(PRODUCT_ID, 1, 10).list()).hasSize(1);
    }

    @Test
    void publicListMasksBuyerButAdminListKeepsRealId() {
        insertOrder(ORDER_ID, BUYER, SELLER, "COMPLETED");
        insertItem(ORDER_ID, PRODUCT_ID, "SEVAL0001");
        CreateEvaluationRequest request = evaluation(5, "满意", false);
        request.setAnonymous(false);
        evaluationService.create(BUYER, ORDER_ID, request);

        List<EvaluationResponse> publicList = evaluationService.listByProduct(PRODUCT_ID, 1, 10).list();
        assertThat(publicList.get(0).getBuyerId()).isNull();
        assertThat(publicList.get(0).getBuyerLabel()).isEqualTo("U2***01");

        List<EvaluationResponse> adminList = evaluationService.adminList(null, PRODUCT_ID, 1, 10).list();
        assertThat(adminList.get(0).getBuyerId()).isEqualTo(BUYER);
    }

    private CreateEvaluationRequest evaluation(int rating, String content, boolean withImage) {
        CreateEvaluationRequest request = new CreateEvaluationRequest();
        request.setRating(rating);
        request.setContent(content);
        request.setAnonymous(true);
        if (withImage) {
            request.setImages(List.of("/images/review-1.png"));
        }
        return request;
    }

    private void insertOrder(String orderId, String buyerId, String sellerId, String status) {
        LocalDateTime now = LocalDateTime.now();
        jdbcTemplate.update("INSERT INTO `order` (order_id, order_no, buyer_id, seller_id, status, total_amount, "
                        + "pay_amount, receiver, receiver_phone, address_full, tracking_no, expire_at, paid_at, "
                        + "created_at, updated_at, created_by, updated_by, is_deleted, inventory_released) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 0, 0)",
                orderId, "NO" + orderId, buyerId, sellerId, status, 10000L, 10000L,
                "测试买家", "13800138000", "测试省 测试市 测试路 1 号", null,
                now.plusMinutes(30), null, now, now, buyerId, buyerId);
    }

    private void insertItem(String orderId, String productId, String skuId) {
        jdbcTemplate.update("INSERT INTO order_item (order_id, sku_id, product_id, product_title, spec_name, "
                        + "price_snapshot, quantity, subtotal, main_image, seller_id, created_at, is_deleted) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 0)",
                orderId, skuId, productId, "测试商品", "默认规格", 10000L, 1, 10000L,
                "/images/test.png", SELLER, LocalDateTime.now());
    }
}
