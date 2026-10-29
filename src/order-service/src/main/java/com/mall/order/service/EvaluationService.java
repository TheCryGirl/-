package com.mall.order.service;

import com.mall.order.common.BizException;
import com.mall.order.common.ErrorCode;
import com.mall.order.common.EvaluationStatus;
import com.mall.order.common.IdGenerator;
import com.mall.order.common.OrderStatus;
import com.mall.order.common.PageResult;
import com.mall.order.common.TimeUtils;
import com.mall.order.dto.request.CreateEvaluationRequest;
import com.mall.order.dto.response.EvaluationResponse;
import com.mall.order.dto.response.EvaluationStatsResponse;
import com.mall.order.entity.Order;
import com.mall.order.entity.OrderEvaluation;
import com.mall.order.mapper.OrderEvaluationMapper;
import com.mall.order.mapper.OrderMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** 订单评价与晒图（任务 #57）。 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EvaluationService {

    private static final int MAX_IMAGES = 3;
    private static final int MAX_CONTENT = 500;
    private static final int MAX_IMAGE_URL = 255;

    private final OrderMapper orderMapper;
    private final OrderEvaluationMapper evaluationMapper;
    private final AuditLogService auditLogService;

    /** 买家对已完成订单发表评价，一个订单只能评价一次。 */
    public EvaluationResponse create(String buyerId, String orderId, CreateEvaluationRequest request) {
        requireUser(buyerId);
        Order order = requireOrder(orderId);
        if (!buyerId.equals(order.getBuyerId())) {
            throw new BizException(ErrorCode.FORBIDDEN, "只能评价自己的订单");
        }
        if (order.getStatus() != OrderStatus.COMPLETED) {
            throw new BizException(ErrorCode.ORDER_STATE_INVALID, "只有已完成的订单可以评价");
        }
        if (evaluationMapper.selectByOrderId(order.getOrderId()) != null) {
            throw new BizException(ErrorCode.EVALUATION_EXISTS, "该订单已评价");
        }
        int rating = request.getRating() == null ? 0 : request.getRating();
        if (rating < 1 || rating > 5) {
            throw new BizException(ErrorCode.PARAM_INVALID, "评分必须在 1~5 星之间");
        }
        String content = trimToNull(request.getContent());
        if (content != null && content.length() > MAX_CONTENT) {
            throw new BizException(ErrorCode.PARAM_INVALID, "评价内容不能超过 " + MAX_CONTENT + " 字");
        }
        List<String> images = normalizeImages(request.getImages());
        if (content == null && images.isEmpty()) {
            throw new BizException(ErrorCode.PARAM_INVALID, "请至少填写评价内容或上传图片");
        }

        LocalDateTime now = LocalDateTime.now();
        OrderEvaluation evaluation = OrderEvaluation.builder()
                .evaluationId("E" + IdGenerator.randomDigits(12))
                .orderId(order.getOrderId())
                .buyerId(buyerId)
                .sellerId(order.getSellerId())
                .rating(rating)
                .content(content)
                .images(images.isEmpty() ? null : String.join(",", images))
                .anonymous(Boolean.TRUE.equals(request.getAnonymous()) ? 1 : 0)
                .status(EvaluationStatus.VISIBLE)
                .createdAt(now)
                .updatedAt(now)
                .build();
        try {
            evaluationMapper.insert(evaluation);
        } catch (DataIntegrityViolationException e) {
            // 并发重复提交时由 order_id 唯一约束兜底
            throw new BizException(ErrorCode.EVALUATION_EXISTS, "该订单已评价");
        }
        log.info("订单评价成功, orderId={}, evaluationId={}, rating={}",
                order.getOrderId(), evaluation.getEvaluationId(), rating);
        return toResponse(evaluation, false);
    }

    /** 订单详情页查询评价：买家、卖家和管理员可见。 */
    public List<EvaluationResponse> listByOrder(String orderId, String userId, String role) {
        requireUser(userId);
        Order order = requireOrder(orderId);
        if (!"ADMIN".equalsIgnoreCase(role)
                && !userId.equals(order.getBuyerId()) && !userId.equals(order.getSellerId())) {
            throw new BizException(ErrorCode.FORBIDDEN, "无权查看该订单评价");
        }
        OrderEvaluation evaluation = evaluationMapper.selectByOrderId(order.getOrderId());
        if (evaluation == null) {
            return List.of();
        }
        return List.of(toResponse(evaluation, false));
    }

    /** 商品详情页的公开评价列表，只返回可见评价。 */
    public PageResult<EvaluationResponse> listByProduct(String productId, int page, int size) {
        String safeProductId = requireText(productId, "商品 ID");
        int safePage = Math.max(1, page);
        int safeSize = Math.min(50, Math.max(1, size));
        List<EvaluationResponse> list = evaluationMapper
                .selectByProduct(safeProductId, (safePage - 1) * safeSize, safeSize)
                .stream().map(item -> toResponse(item, false)).toList();
        long total = evaluationMapper.countByProduct(safeProductId);
        return PageResult.of(list, total, safePage, safeSize);
    }

    public EvaluationStatsResponse stats(String productId) {
        String safeProductId = requireText(productId, "商品 ID");
        EvaluationStatsResponse stats = evaluationMapper.selectProductStats(safeProductId);
        return stats == null ? emptyStats() : stats;
    }

    /** 管理端评价列表，可按状态和商品筛选。 */
    public PageResult<EvaluationResponse> adminList(String status, String productId, int page, int size) {
        String statusFilter = null;
        if (status != null && !status.isBlank()) {
            try {
                statusFilter = EvaluationStatus.valueOf(status.trim().toUpperCase()).name();
            } catch (IllegalArgumentException e) {
                throw new BizException(ErrorCode.PARAM_INVALID, "评价状态只能是 VISIBLE 或 HIDDEN");
            }
        }
        String productFilter = trimToNull(productId);
        int safePage = Math.max(1, page);
        int safeSize = Math.min(100, Math.max(1, size));
        List<EvaluationResponse> list = evaluationMapper
                .selectAdminPage(statusFilter, productFilter, (safePage - 1) * safeSize, safeSize)
                .stream().map(item -> toResponse(item, true)).toList();
        long total = evaluationMapper.countAdmin(statusFilter, productFilter);
        return PageResult.of(list, total, safePage, safeSize);
    }

    public void hide(String evaluationId, String operatorId, String role) {
        changeStatus(evaluationId, operatorId, role, EvaluationStatus.HIDDEN);
    }

    public void show(String evaluationId, String operatorId, String role) {
        changeStatus(evaluationId, operatorId, role, EvaluationStatus.VISIBLE);
    }

    private void changeStatus(String evaluationId, String operatorId, String role,
                              EvaluationStatus target) {
        requireAdmin(role);
        requireUser(operatorId);
        OrderEvaluation evaluation = evaluationMapper.selectById(requireText(evaluationId, "评价 ID"));
        if (evaluation == null) {
            throw new BizException(ErrorCode.EVALUATION_NOT_FOUND, "评价不存在");
        }
        if (evaluation.getStatus() == target) {
            return;
        }
        evaluationMapper.updateStatus(evaluation.getEvaluationId(), target.name());
        String action = target == EvaluationStatus.HIDDEN ? "EVALUATION_HIDE" : "EVALUATION_SHOW";
        String detail = target == EvaluationStatus.HIDDEN ? "隐藏订单评价" : "恢复订单评价显示";
        auditLogService.record(operatorId, role, action, "EVALUATION", evaluation.getEvaluationId(),
                detail + "，orderId=" + evaluation.getOrderId());
    }

    private EvaluationResponse toResponse(OrderEvaluation evaluation, boolean adminView) {
        EvaluationResponse response = new EvaluationResponse();
        response.setEvaluationId(evaluation.getEvaluationId());
        response.setOrderId(evaluation.getOrderId());
        response.setRating(evaluation.getRating());
        response.setContent(evaluation.getContent());
        response.setImages(splitImages(evaluation.getImages()));
        boolean anonymous = evaluation.getAnonymous() != null && evaluation.getAnonymous() == 1;
        response.setAnonymous(anonymous);
        response.setStatus(evaluation.getStatus() == null ? null : evaluation.getStatus().name());
        response.setCreatedAt(TimeUtils.format(evaluation.getCreatedAt()));
        response.setBuyerLabel(anonymous ? "匿名用户" : maskBuyer(evaluation.getBuyerId()));
        if (adminView) {
            response.setBuyerId(evaluation.getBuyerId());
        }
        return response;
    }

    private static EvaluationStatsResponse emptyStats() {
        EvaluationStatsResponse stats = new EvaluationStatsResponse();
        stats.setTotal(0);
        stats.setAverage(0);
        return stats;
    }

    private static List<String> normalizeImages(List<String> raw) {
        if (raw == null || raw.isEmpty()) {
            return List.of();
        }
        List<String> images = new ArrayList<>();
        for (String value : raw) {
            String url = trimToNull(value);
            if (url == null) {
                continue;
            }
            if (url.length() > MAX_IMAGE_URL) {
                throw new BizException(ErrorCode.PARAM_INVALID, "图片地址长度不能超过 " + MAX_IMAGE_URL);
            }
            if (!url.startsWith("/") && !url.startsWith("http://") && !url.startsWith("https://")) {
                throw new BizException(ErrorCode.PARAM_INVALID, "图片地址不合法");
            }
            images.add(url);
        }
        if (images.size() > MAX_IMAGES) {
            throw new BizException(ErrorCode.PARAM_INVALID, "最多上传 " + MAX_IMAGES + " 张图片");
        }
        return images;
    }

    private static List<String> splitImages(String images) {
        if (images == null || images.isBlank()) {
            return List.of();
        }
        return Arrays.stream(images.split(","))
                .map(String::trim)
                .filter(value -> !value.isEmpty())
                .toList();
    }

    private static String maskBuyer(String buyerId) {
        if (buyerId == null || buyerId.isBlank()) {
            return "匿名用户";
        }
        if (buyerId.length() <= 4) {
            return "用户***";
        }
        return buyerId.substring(0, 2) + "***" + buyerId.substring(buyerId.length() - 2);
    }

    private Order requireOrder(String orderId) {
        Order order = orderId == null ? null : orderMapper.selectById(orderId.trim());
        if (order == null) {
            throw new BizException(ErrorCode.ORDER_NOT_FOUND, "订单不存在");
        }
        return order;
    }

    private static void requireAdmin(String role) {
        if (!"ADMIN".equalsIgnoreCase(role)) {
            throw new BizException(ErrorCode.FORBIDDEN, "无权限操作评价");
        }
    }

    private static void requireUser(String userId) {
        if (userId == null || userId.isBlank()) {
            throw new BizException(ErrorCode.UNAUTHORIZED, "未登录或用户信息缺失");
        }
    }

    private static String requireText(String value, String field) {
        String trimmed = trimToNull(value);
        if (trimmed == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, field + "不能为空");
        }
        return trimmed;
    }

    private static String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
