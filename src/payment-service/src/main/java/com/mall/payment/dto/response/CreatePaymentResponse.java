package com.mall.payment.dto.response;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 发起支付的返回结构，与《05-接口契约》6.3 节一致。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CreatePaymentResponse {

    private String paymentId;
    private String payNo;
    private Long amount;
    private String payUrl;
}
