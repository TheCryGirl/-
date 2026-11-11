package com.mall.order.controller;

import com.mall.order.dto.response.AdminFinanceSummaryResponse;
import com.mall.order.service.OrderService;
import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 管理员资金汇总端点的路由与鉴权契约（轻量测试，不启动 Spring 上下文）。
 *
 * <p>校验三件事：路径是 /admin/finance-summary（不能在 /{orderId} 之前被吞掉）、
 * 身份只来自 X-User-Id / X-User-Role 头、参数里没有任何 role 之类的请求参数可用来授权。
 */
class OrderAdminFinanceSummaryRouteTest {

    @Test
    void adminFinanceSummaryIsMappedUnderAdminPath() {
        Method method = adminMethod();
        GetMapping mapping = method.getAnnotation(GetMapping.class);
        assertThat(mapping).isNotNull();
        assertThat(List.of(mapping.value())).containsExactly("/admin/finance-summary");
    }

    @Test
    void identityComesOnlyFromHeadersNotQueryParameters() {
        Method method = adminMethod();
        List<Parameter> parameters = Arrays.asList(method.getParameters());
        List<String> headerNames = parameters.stream()
                .map(parameter -> parameter.getAnnotation(RequestHeader.class))
                .filter(java.util.Objects::nonNull)
                .map(RequestHeader::value)
                .toList();
        assertThat(headerNames).containsExactlyInAnyOrder("X-User-Id", "X-User-Role");
        assertThat(parameters).noneMatch(parameter -> parameter.isAnnotationPresent(RequestParam.class));
    }

    @Test
    void otherOrderRoutesKeepTheirPaths() {
        assertThat(List.of(OrderController.class.getAnnotation(
                org.springframework.web.bind.annotation.RequestMapping.class).value()))
                .contains("/orders", "/api/orders");
        assertThat(pathsOf("list")).isEmpty();
        assertThat(pathsOf("detail")).contains("/{orderId}");
    }

    private static List<String> pathsOf(String name) {
        return Arrays.stream(OrderController.class.getMethods())
                .filter(method -> method.getName().equals(name))
                .findFirst()
                .map(method -> List.of(method.getAnnotation(GetMapping.class).value()))
                .orElseGet(List::of);
    }

    private static Method adminMethod() {
        return Arrays.stream(OrderController.class.getMethods())
                .filter(method -> method.getName().equals("adminFinanceSummary"))
                .findFirst()
                .orElseThrow(() -> new AssertionError("OrderController 缺少 adminFinanceSummary 端点"));
    }

    @Test
    void endpointReturnsAdminFinanceSummaryPayload() {
        assertThat(adminMethod().getGenericReturnType().getTypeName())
                .contains(AdminFinanceSummaryResponse.class.getSimpleName());
        assertThat(OrderService.class.getMethods())
                .anyMatch(method -> method.getReturnType().equals(AdminFinanceSummaryResponse.class));
    }
}
