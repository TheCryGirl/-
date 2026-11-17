package com.mall.order.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 手工补录物流轨迹（任务 #138）。
 *
 * <p>本期不接第三方快递 API，轨迹由卖家或管理员逐条录入。
 * {@code occurredAt} 允许为空，缺省取当前时间；补录历史节点时显式传入，
 * 时间线始终按 occurredAt 正序展示，与录入顺序无关。
 */
@Data
public class LogisticsTrackRequest {

    @NotBlank(message = "轨迹状态不能为空")
    private String status;

    @NotBlank(message = "轨迹描述不能为空")
    @Size(max = 255, message = "轨迹描述长度不能超过 255")
    private String description;

    /** 格式 yyyy-MM-dd HH:mm:ss；为空取当前时间 */
    private String occurredAt;

    /** 原始报文，可选，最长 2048；便于以后对接外部接口时对账 */
    @Size(max = 2048, message = "原始报文长度不能超过 2048")
    private String rawJson;
}
