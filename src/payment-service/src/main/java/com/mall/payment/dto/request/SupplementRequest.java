package com.mall.payment.dto.request;

import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/**
 * 仲裁期间双方补充材料（#143）。仅 ARBITRATING 状态可提交，买卖双方与管理员均可。
 * 补充说明与凭证至少提供其一；凭证单次最多 3 张，材料对双方可见。
 */
@Data
public class SupplementRequest {

    @Size(max = 255, message = "补充说明过长")
    private String remark;

    @Size(max = 3, message = "单次最多上传 3 张凭证")
    private List<@Size(max = 255, message = "凭证路径过长") String> attachmentUrls;
}