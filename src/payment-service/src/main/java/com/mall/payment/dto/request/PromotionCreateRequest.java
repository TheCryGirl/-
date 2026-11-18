package com.mall.payment.dto.request;

import com.mall.payment.common.PromotionScope;
import com.mall.payment.common.PromotionType;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 管理员创建满减/满折活动（#140）。
 */
@Data
public class PromotionCreateRequest {

    @NotNull(message = "活动范围不能为空")
    private PromotionScope scope;

    @Size(max = 32, message = "店铺ID过长")
    private String shopId;

    @NotNull(message = "活动类型不能为空")
    private PromotionType type;

    @NotNull(message = "门槛不能为空")
    private Long threshold;

    @NotNull(message = "减免/折扣不能为空")
    @Positive(message = "减免/折扣必须大于 0")
    private Long value;

    @NotNull(message = "是否可叠加不能为空")
    private Boolean stackable;

    @NotNull(message = "开始时间不能为空")
    private LocalDateTime startAt;

    @NotNull(message = "结束时间不能为空")
    private LocalDateTime endAt;
}