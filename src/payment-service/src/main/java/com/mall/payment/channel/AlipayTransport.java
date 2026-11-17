package com.mall.payment.channel;

import java.util.Map;

/**
 * 支付宝网关 HTTP 传输层，独立出来便于测试替换。
 */
@FunctionalInterface
public interface AlipayTransport {

    /** 以 application/x-www-form-urlencoded POST 网关，返回响应 body */
    String post(Map<String, String> formParams);
}