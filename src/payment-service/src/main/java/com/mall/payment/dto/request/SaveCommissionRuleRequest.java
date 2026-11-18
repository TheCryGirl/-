package com.mall.payment.dto.request;

public record SaveCommissionRuleRequest(String scope, String targetId, Integer rate) {}
