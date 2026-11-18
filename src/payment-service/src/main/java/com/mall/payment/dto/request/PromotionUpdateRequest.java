package com.mall.payment.dto.request;

import com.mall.payment.common.PromotionStatus;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 管理员更新活动状态（#140）。
 */
@Data
public class PromotionUpdateRequest {

    @NotNull(message = "活动状态不能为空")
    private PromotionStatus status;
}