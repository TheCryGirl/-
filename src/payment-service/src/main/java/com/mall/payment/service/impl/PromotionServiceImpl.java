package com.mall.payment.service.impl;

import com.mall.payment.common.BizException;
import com.mall.payment.common.ErrorCode;
import com.mall.payment.common.IdGenerator;
import com.mall.payment.common.PageResult;
import com.mall.payment.common.PromotionScope;
import com.mall.payment.common.PromotionStatus;
import com.mall.payment.dto.request.PromotionCreateRequest;
import com.mall.payment.dto.request.PromotionUpdateRequest;
import com.mall.payment.dto.response.PromotionResponse;
import com.mall.payment.entity.Promotion;
import com.mall.payment.mapper.PromotionMapper;
import com.mall.payment.service.PromotionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;

/**
 * 满减/满折实现（#140）。活动与优惠券的叠加关系在计价链（order-service）里校验，
 * 这里只负责配置的增改查与生效清单。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PromotionServiceImpl implements PromotionService {

    private static final int MAX_PAGE_SIZE = 100;

    private final PromotionMapper promotionMapper;

    @Override
    public PageResult<PromotionResponse> adminList(String status, int page, int size) {
        int safePage = Math.max(page, 1);
        int safeSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        PromotionStatus filter = parseStatus(status);
        long total = promotionMapper.count(filter);
        List<PromotionResponse> list = promotionMapper
                .selectPage(filter, (safePage - 1) * safeSize, safeSize)
                .stream().map(PromotionResponse::from).toList();
        return PageResult.of(list, total, safePage, safeSize);
    }

    @Override
    public PromotionResponse adminCreate(String operatorId, PromotionCreateRequest request) {
        if (request.getScope() == PromotionScope.SHOP
                && (request.getShopId() == null || request.getShopId().isBlank())) {
            throw new BizException(ErrorCode.PARAM_INVALID, "店铺活动必须指定店铺");
        }
        if (request.getStartAt().isAfter(request.getEndAt())) {
            throw new BizException(ErrorCode.PARAM_INVALID, "开始时间不能晚于结束时间");
        }
        Promotion promotion = new Promotion();
        promotion.setPromotionId(IdGenerator.promotionId());
        promotion.setScope(request.getScope());
        promotion.setShopId(request.getScope() == PromotionScope.SHOP ? request.getShopId() : null);
        promotion.setType(request.getType());
        promotion.setThreshold(request.getThreshold());
        promotion.setValue(request.getValue());
        promotion.setStackable(request.getStackable());
        promotion.setStartAt(request.getStartAt());
        promotion.setEndAt(request.getEndAt());
        promotion.setStatus(PromotionStatus.ACTIVE);
        promotion.setCreatedBy(operatorId);
        promotion.setUpdatedBy(operatorId);
        promotion.setIsDeleted(false);
        promotionMapper.insert(promotion);
        return PromotionResponse.from(promotionMapper.selectById(promotion.getPromotionId()));
    }

    @Override
    public PromotionResponse adminUpdate(String operatorId, String promotionId,
                                         PromotionUpdateRequest request) {
        Promotion promotion = promotionMapper.selectById(promotionId);
        if (promotion == null) {
            throw new BizException(ErrorCode.PROMOTION_NOT_FOUND, "活动不存在");
        }
        promotionMapper.updateStatus(promotionId, request.getStatus(), operatorId);
        return PromotionResponse.from(promotionMapper.selectById(promotionId));
    }

    @Override
    public List<PromotionResponse> active() {
        return promotionMapper.selectActive(LocalDateTime.now()).stream()
                .map(PromotionResponse::from).toList();
    }

    private PromotionStatus parseStatus(String status) {
        if (status == null || status.isBlank()) {
            return null;
        }
        try {
            return PromotionStatus.valueOf(status.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new BizException(ErrorCode.PARAM_INVALID, "活动状态不合法");
        }
    }
}