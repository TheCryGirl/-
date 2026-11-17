package com.mall.user.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/** 管理员变更工单状态（#142）。 */
@Data
public class UpdateTicketStatusRequest {

    @NotBlank(message = "状态不能为空")
    private String status;
}