package com.mall.user.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/**
 * 卖家提交入驻申请入参（#144）。
 *
 * <p>店名与联系方式直接写入 shop，主体类型、证照编号与资质材料存进
 * shop.qualification_json，审核阶段管理员只读。
 */
@Data
public class ShopApplyRequest {

    @NotBlank(message = "店铺名称不能为空")
    @Size(max = 64, message = "店铺名称不能超过 64 字")
    private String name;

    @Pattern(regexp = "^$|^1[3-9]\\d{9}$|^0\\d{2,3}-?\\d{7,8}$", message = "联系电话格式不正确")
    private String contactPhone;

    @Size(max = 64, message = "在线联系方式不能超过 64 字")
    private String contactWechat;

    /** 主体类型：PERSONAL 个人 / INDIVIDUAL 个体工商户 / ENTERPRISE 企业 */
    @NotBlank(message = "主体类型不能为空")
    private String subjectType;

    @Size(max = 64, message = "证照编号不能超过 64 字")
    private String licenseNo;

    /** 资质材料地址，最多 9 项 */
    @Size(max = 9, message = "资质材料最多 9 项")
    private List<String> materials;
}
