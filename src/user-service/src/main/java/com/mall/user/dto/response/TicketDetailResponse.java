package com.mall.user.dto.response;

import lombok.Data;

import java.util.List;

/** 工单详情（#142）：工单基本信息 + 消息列表。 */
@Data
public class TicketDetailResponse {

    private TicketResponse ticket;
    private List<TicketMessageResponse> messages;
}