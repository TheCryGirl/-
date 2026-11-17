package com.mall.user.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/** 回复工单（#142）。 */
@Data
public class ReplyTicketRequest {

    @NotBlank(message = "回复内容不能为空")
    @Size(max = 1000, message = "回复内容过长")
    private String content;
}