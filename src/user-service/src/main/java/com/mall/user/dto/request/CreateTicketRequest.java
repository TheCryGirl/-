package com.mall.user.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/** 创建客服工单（#142）。 */
@Data
public class CreateTicketRequest {

    @NotBlank(message = "工单类型不能为空")
    private String type;

    @NotBlank(message = "标题不能为空")
    @Size(max = 100, message = "标题过长")
    private String title;

    @Size(max = 32, message = "业务ID过长")
    private String bizId;

    @Size(max = 1000, message = "内容过长")
    private String content;
}