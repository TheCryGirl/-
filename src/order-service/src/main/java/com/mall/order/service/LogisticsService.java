package com.mall.order.service;

import com.mall.order.common.BizException;
import com.mall.order.common.ErrorCode;
import com.mall.order.common.ExpressCompany;
import com.mall.order.common.IdGenerator;
import com.mall.order.common.LogisticsTrackStatus;
import com.mall.order.common.OrderStatus;
import com.mall.order.common.TimeUtils;
import com.mall.order.dto.request.LogisticsTrackRequest;
import com.mall.order.dto.response.LogisticsTrackResponse;
import com.mall.order.dto.response.OrderLogisticsResponse;
import com.mall.order.entity.Order;
import com.mall.order.entity.OrderLogisticsTrack;
import com.mall.order.mapper.OrderLogisticsTrackMapper;
import com.mall.order.mapper.OrderMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 物流履约：轨迹读取与补录（任务 #138）。
 *
 * <p>发货本身仍走 {@link OrderService#ship}（状态机与 CAS 在那边），
 * 这里只处理「发货之后」的物流信息，避免把状态迁移逻辑拆到两个类里。
 *
 * <p>轨迹不接第三方快递 API，全部为手工录入；因此这里不做任何外部调用，
 * 也不存在外部失败需要补偿的分支。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LogisticsService {

    private final OrderMapper orderMapper;
    private final OrderLogisticsTrackMapper trackMapper;
    private final AutoConfirmPolicy autoConfirmPolicy;

    /**
     * 查询订单物流。未发货订单返回空轨迹 + 明确提示，不报错。
     *
     * @param orderId 订单ID
     * @param userId  当前用户，用于归属校验
     * @param role    当前角色，ADMIN 可查任意订单
     */
    public OrderLogisticsResponse detail(String orderId, String userId, String role) {
        Order order = requireOrder(orderId);
        checkReadAccess(order, userId, role);

        long autoConfirmMinutes = autoConfirmPolicy.minutes();
        OrderLogisticsResponse response = new OrderLogisticsResponse();
        response.setOrderId(order.getOrderId());
        response.setAutoConfirmMinutes(autoConfirmMinutes);

        boolean shipped = order.getStatus() == OrderStatus.SHIPPED
                || order.getStatus() == OrderStatus.COMPLETED;
        response.setShipped(shipped);
        response.setExpressCompany(order.getExpressCompany());
        response.setExpressCompanyLabel(ExpressCompany.labelOf(order.getExpressCompany()));
        response.setTrackingNo(order.getTrackingNo());
        response.setShippedAt(TimeUtils.format(order.getShippedAt()));

        List<LogisticsTrackResponse> tracks = trackMapper.selectByOrderId(order.getOrderId()).stream()
                .map(this::toResponse)
                .toList();
        response.setTracks(tracks);

        if (!shipped) {
            response.setMessage("订单尚未发货，暂无物流信息");
            return response;
        }
        if (tracks.isEmpty()) {
            response.setMessage("已发货，物流更新中");
        }
        // 只有仍在 SHIPPED 的单才有「还剩几天」的说法；已完成/已退款不再倒计时
        if (order.getStatus() == OrderStatus.SHIPPED) {
            response.setAutoConfirmRemainingMinutes(
                    autoConfirmPolicy.remainingMinutes(order.getShippedAt(), autoConfirmMinutes));
        }
        return response;
    }

    /**
     * 手工补录一条轨迹，卖家（本单）或管理员可操作。
     *
     * <p>允许重复录入同一状态（例如多次「运输中」），只按发生时间排序展示；
     * 但不允许把轨迹录到未发货的订单上——没有发货就没有轨迹，避免脏数据。
     */
    public LogisticsTrackResponse append(String orderId, String operatorId, String role,
                                         LogisticsTrackRequest request) {
        Order order = requireOrder(orderId);
        checkOperateAccess(order, operatorId, role);

        if (order.getStatus() != OrderStatus.SHIPPED && order.getStatus() != OrderStatus.COMPLETED) {
            throw new BizException(ErrorCode.ORDER_STATE_INVALID, "订单尚未发货，不能录入物流轨迹");
        }
        LogisticsTrackStatus status = LogisticsTrackStatus.parse(request.getStatus());
        if (status == null) {
            throw new BizException(ErrorCode.PARAM_INVALID,
                    "轨迹状态不合法，只能是 COLLECTED/IN_TRANSIT/OUT_FOR_DELIVERY/SIGNED/EXCEPTION");
        }
        String description = requireText(request.getDescription(), "轨迹描述");
        LocalDateTime occurredAt = parseOccurredAt(request.getOccurredAt());

        OrderLogisticsTrack track = OrderLogisticsTrack.builder()
                .trackId(IdGenerator.trackId())
                .orderId(order.getOrderId())
                .status(status)
                .description(description)
                .occurredAt(occurredAt)
                .rawJson(trimToNull(request.getRawJson()))
                .createdAt(LocalDateTime.now())
                .createdBy(operatorId)
                .build();
        trackMapper.insert(track);
        log.info("物流轨迹已补录, orderId={}, trackId={}, status={}, operator={}",
                orderId, track.getTrackId(), status, operatorId);
        return toResponse(track);
    }

    private LogisticsTrackResponse toResponse(OrderLogisticsTrack track) {
        LogisticsTrackStatus status = track.getStatus();
        return new LogisticsTrackResponse(track.getTrackId(),
                status == null ? null : status.name(),
                status == null ? null : status.getLabel(),
                track.getDescription(),
                TimeUtils.format(track.getOccurredAt()),
                status != null && status.isAbnormal());
    }

    private static LocalDateTime parseOccurredAt(String raw) {
        if (raw == null || raw.isBlank()) {
            return LocalDateTime.now();
        }
        try {
            return TimeUtils.parse(raw);
        } catch (RuntimeException e) {
            throw new BizException(ErrorCode.PARAM_INVALID, "轨迹发生时间格式应为 yyyy-MM-dd HH:mm:ss");
        }
    }

    private void checkOperateAccess(Order order, String userId, String role) {
        if ("ADMIN".equalsIgnoreCase(role)) {
            return;
        }
        if (userId == null || userId.isBlank() || !userId.equals(order.getSellerId())) {
            throw new BizException(ErrorCode.FORBIDDEN, "无权操作该订单的物流信息");
        }
    }

    private void checkReadAccess(Order order, String userId, String role) {
        if ("ADMIN".equalsIgnoreCase(role)) {
            return;
        }
        if (userId == null || userId.isBlank()
                || (!userId.equals(order.getBuyerId()) && !userId.equals(order.getSellerId()))) {
            throw new BizException(ErrorCode.FORBIDDEN, "无权查看该订单的物流信息");
        }
    }

    private Order requireOrder(String orderId) {
        Order order = orderId == null ? null : orderMapper.selectById(orderId.trim());
        if (order == null) {
            throw new BizException(ErrorCode.ORDER_NOT_FOUND, "订单不存在");
        }
        return order;
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new BizException(ErrorCode.PARAM_INVALID, field + "不能为空");
        }
        return value.trim();
    }

    private static String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
