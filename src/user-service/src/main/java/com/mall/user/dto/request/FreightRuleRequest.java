package com.mall.user.dto.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 单条分地区运费规则（#136）。
 */
@Data
public class FreightRuleRequest {

    /** 省级名称（如「河北省」）或 DEFAULT 兜底 */
    @NotBlank(message = "地区不能为空")
    @Size(max = 32, message = "地区名称最多 32 个字符")
    private String regionCode;

    /** 适用该规则的最低商品金额，单位分 */
    @NotNull(message = "最低金额不能为空")
    @Min(value = 0, message = "最低金额不能为负数")
    private Long minAmount;

    /** 运费，单位分 */
    @NotNull(message = "运费不能为空")
    @Min(value = 0, message = "运费不能为负数")
    private Long fee;
}
