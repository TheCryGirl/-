package com.mall.payment.dto.request;

import com.mall.payment.common.CouponType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 管理员创建优惠券模板（#140）。
 */
@Data
public class CouponCreateRequest {

    @NotBlank(message = "店铺ID不能为空")
    @Size(max = 32, message = "店铺ID过长")
    private String shopId;

    @NotBlank(message = "券名称不能为空")
    @Size(max = 64, message = "券名称过长")
    private String name;

    @NotNull(message = "券类型不能为空")
    private CouponType type;

    @NotNull(message = "券面额/折扣不能为空")
    @Positive(message = "券面额/折扣必须大于 0")
    private Long value;

    @NotNull(message = "使用门槛不能为空")
    private Long minAmount;

    @NotNull(message = "发行总量不能为空")
    @Positive(message = "发行总量必须大于 0")
    private Integer totalQty;

    @NotNull(message = "开始时间不能为空")
    private LocalDateTime startAt;

    @NotNull(message = "结束时间不能为空")
    private LocalDateTime endAt;
}