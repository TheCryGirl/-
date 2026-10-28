package com.mall.user.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 店铺资料入参。卖家首次提交时为新建，之后为修改。
 */
@Data
public class ShopRequest {

    @NotBlank(message = "店铺名称不能为空")
    @Size(max = 64, message = "店铺名称不能超过 64 字")
    private String name;

    @Size(max = 255, message = "店铺头像地址不能超过 255 字")
    private String logo;

    @Size(max = 255, message = "店铺简介不能超过 255 字")
    private String description;

    /** 联系电话，允许手机号或带区号的座机 */
    @Pattern(regexp = "^$|^1[3-9]\\d{9}$|^0\\d{2,3}-?\\d{7,8}$", message = "联系电话格式不正确")
    private String contactPhone;

    @Size(max = 64, message = "在线联系方式不能超过 64 字")
    private String contactWechat;
}
