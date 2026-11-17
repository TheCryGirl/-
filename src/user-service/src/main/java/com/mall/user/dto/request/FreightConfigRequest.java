package com.mall.user.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/**
 * 卖家保存店铺运费配置（#136）：整体覆盖，先删旧规则再写新规则。
 */
@Data
public class FreightConfigRequest {

    @NotBlank(message = "模板名称不能为空")
    @Size(max = 64, message = "模板名称最多 64 个字符")
    private String name;

    /** 包邮门槛，单位分；0 表示不包邮 */
    @NotNull(message = "包邮门槛不能为空")
    @Min(value = 0, message = "包邮门槛不能为负数")
    private Long freeThreshold;

    /** 分地区运费规则，可以为空（等价于全国不收运费） */
    @Valid
    @Size(max = 50, message = "运费规则最多 50 条")
    private List<FreightRuleRequest> rules;
}
