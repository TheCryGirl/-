package com.mall.payment.dto.response;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 重复回调的结果，duplicate=true 表示唯一键命中去重。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class MockDuplicateResponse {

    private String tradeNo;
    private boolean duplicate;
    private int code;
    private String message;
}
