package com.mall.payment.controller;

import com.mall.payment.dto.response.AdminFinanceSummaryResponse;
import com.mall.payment.service.FinanceSummaryService;
import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 管理员资金汇总端点的路由与鉴权契约（轻量测试，不启动 Spring 上下文）。
 *
 * <p>校验：路径 /admin/finance-summary、身份只来自 X-User-Id / X-User-Role 头、
 * 没有可用的 query 参数授权入口，且接口返回 AdminFinanceSummaryResponse。
 */
class PaymentAdminFinanceSummaryRouteTest {

    @Test
    void adminFinanceSummaryIsMappedUnderAdminPath() {
        GetMapping mapping = adminMethod().getAnnotation(GetMapping.class);
        assertThat(mapping).isNotNull();
        assertThat(List.of(mapping.value())).containsExactly("/admin/finance-summary");
    }

    @Test
    void identityComesOnlyFromHeadersNotQueryParameters() {
        List<Parameter> parameters = Arrays.asList(adminMethod().getParameters());
        List<String> headerNames = parameters.stream()
                .map(parameter -> parameter.getAnnotation(RequestHeader.class))
                .filter(Objects::nonNull)
                .map(RequestHeader::value)
                .toList();
        assertThat(headerNames).containsExactlyInAnyOrder("X-User-Id", "X-User-Role");
        assertThat(parameters).noneMatch(parameter -> parameter.isAnnotationPresent(RequestParam.class));
    }

    @Test
    void serviceContractReturnsAdminFinanceSummary() {
        assertThat(FinanceSummaryService.class.getMethods())
                .anyMatch(method -> method.getReturnType().equals(AdminFinanceSummaryResponse.class));
        assertThat(adminMethod().getGenericReturnType().getTypeName())
                .contains(AdminFinanceSummaryResponse.class.getSimpleName());
    }

    private static Method adminMethod() {
        return Arrays.stream(PaymentController.class.getMethods())
                .filter(method -> method.getName().equals("adminFinanceSummary"))
                .findFirst()
                .orElseThrow(() -> new AssertionError("PaymentController 缺少 adminFinanceSummary 端点"));
    }
}
