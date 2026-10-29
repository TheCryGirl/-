package com.mall.gateway.filter;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 网关白名单判定（安全边界）。
 *
 * <p>白名单决定哪些接口可以匿名访问，规则写错就是越权或误拦，
 * 所以这里按「方法 + 路径」逐条钉住。
 */
class JwtAuthFilterWhiteListTest {

    private JwtAuthFilter filter;
    private Method isWhiteListed;

    @BeforeEach
    void setUp() throws Exception {
        // 构造器只校验密钥长度，不需要真实 Nacos
        filter = new JwtAuthFilter("test-secret-key-for-unit-test-0123456789abcd");
        isWhiteListed = JwtAuthFilter.class.getDeclaredMethod("isWhiteListed", HttpMethod.class, String.class);
        isWhiteListed.setAccessible(true);
    }

    private boolean white(HttpMethod method, String path) throws Exception {
        return (boolean) isWhiteListed.invoke(filter, method, path);
    }

    @Test
    @DisplayName("登录注册与支付回调可匿名 POST")
    void publicPostAllowed() throws Exception {
        assertTrue(white(HttpMethod.POST, "/api/users/register"));
        assertTrue(white(HttpMethod.POST, "/api/users/login"));
        assertTrue(white(HttpMethod.POST, "/api/payments/callback"));
    }

    @Test
    @DisplayName("其他 POST 一律不匿名，尤其是商品写操作")
    void otherPostsRequireAuth() throws Exception {
        assertFalse(white(HttpMethod.POST, "/api/products"));
        assertFalse(white(HttpMethod.POST, "/api/orders"));
        assertFalse(white(HttpMethod.POST, "/api/cart/items"));
        assertFalse(white(HttpMethod.POST, "/api/payments"));
    }

    @Test
    @DisplayName("分类、图片、健康检查可匿名 GET")
    void publicGetAllowed() throws Exception {
        assertTrue(white(HttpMethod.GET, "/api/categories"));
        assertTrue(white(HttpMethod.GET, "/api/categories/C01"));
        assertTrue(white(HttpMethod.GET, "/images/a.png"));
        assertTrue(white(HttpMethod.GET, "/actuator/health"));
    }

    @Test
    @DisplayName("商品列表与详情可匿名 GET")
    void productReadAllowed() throws Exception {
        assertTrue(white(HttpMethod.GET, "/api/products"));
        assertTrue(white(HttpMethod.GET, "/api/products/P9806958711"));
    }

    @Test
    @DisplayName("/api/products/mine 等保留段必须登录")
    void productReservedSegmentsRequireAuth() throws Exception {
        assertFalse(white(HttpMethod.GET, "/api/products/mine"));
        assertFalse(white(HttpMethod.GET, "/api/products/skus"));
        assertFalse(white(HttpMethod.GET, "/api/products/images"));
    }

    @Test
    @DisplayName("订单、购物车、支付查询都不匿名")
    void businessGetRequiresAuth() throws Exception {
        assertFalse(white(HttpMethod.GET, "/api/orders"));
        assertFalse(white(HttpMethod.GET, "/api/orders/O1"));
        assertFalse(white(HttpMethod.GET, "/api/cart"));
        assertFalse(white(HttpMethod.GET, "/api/payments/PAY1"));
        assertFalse(white(HttpMethod.GET, "/api/users/me"));
    }

    @Test
    @DisplayName("平台配置接口不匿名")
    void configRequiresAuth() throws Exception {
        assertFalse(white(HttpMethod.GET, "/api/config"));
        assertFalse(white(HttpMethod.GET, "/api/config/history"));
        assertFalse(white(HttpMethod.PUT, "/api/config"));
    }

    @Test
    @DisplayName("PUT / DELETE 等写方法不走白名单")
    void writeMethodsNotWhitelisted() throws Exception {
        assertFalse(white(HttpMethod.PUT, "/api/users/register"));
        assertFalse(white(HttpMethod.DELETE, "/api/categories/C01"));
        assertFalse(white(HttpMethod.PUT, "/api/products/P1"));
    }

    @Test
    @DisplayName("跨域预检直接放行")
    void optionsAlwaysAllowed() throws Exception {
        assertTrue(white(HttpMethod.OPTIONS, "/api/orders"));
        assertTrue(white(HttpMethod.OPTIONS, "/api/anything"));
    }

    @Test
    @DisplayName("商品评价展示可匿名 GET，评价管理与审计日志必须登录")
    void evaluationAndAuditRules() throws Exception {
        assertTrue(white(HttpMethod.GET, "/api/evaluations/products/P1"));
        assertTrue(white(HttpMethod.GET, "/api/evaluations/products/P1/stats"));
        assertFalse(white(HttpMethod.GET, "/api/evaluations"));
        assertFalse(white(HttpMethod.PUT, "/api/evaluations/E1/hide"));
        assertFalse(white(HttpMethod.GET, "/api/audit-logs"));
    }
}
