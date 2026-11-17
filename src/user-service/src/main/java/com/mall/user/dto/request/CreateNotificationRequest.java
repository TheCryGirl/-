package com.mall.user.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/** 服务间写通知的入参（#139），必须带内部令牌。 */
@Data
public class CreateNotificationRequest {

    @NotBlank(message = "接收人不能为空")
    private String userId;

    @NotBlank(message = "通知类型不能为空")
    private String type;

    @NotBlank(message = "标题不能为空")
    @Size(max = 64, message = "标题过长")
    private String title;

    @NotBlank(message = "内容不能为空")
    @Size(max = 255, message = "内容过长")
    private String content;

    @Size(max = 32, message = "业务ID过长")
    private String bizId;

    @Size(max = 16, message = "业务类型过长")
    private String bizType;
}