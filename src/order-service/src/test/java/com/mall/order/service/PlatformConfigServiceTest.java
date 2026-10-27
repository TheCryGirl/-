package com.mall.order.service;

import com.mall.order.common.BizException;
import com.mall.order.mapper.ConfigAuditMapper;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 平台配置的解析与校验（任务 #43）。
 *
 * <p>不连真实 Nacos：用 mock 替掉 NacosConfigService，只验证纯逻辑。
 */
class PlatformConfigServiceTest {

    // 纯解析/构建方法测真实逻辑，只有网络方法被 stub
    private final NacosConfigService nacos = mock(NacosConfigService.class, CALLS_REAL_METHODS);
    private final ConfigAuditMapper auditMapper = mock(ConfigAuditMapper.class);
    private final PlatformConfigService service = new PlatformConfigService(nacos, auditMapper);

    @Test
    void parsesWhitelistKeysFromYml() {
        String yml = "order.timeout.minutes: 15\norder.timeout.batch-size: 50\n";
        Map<String, String> values = nacos.parseValues(yml, Map.of(
                "order.timeout.minutes", "30", "order.timeout.batch-size", "100"));
        assertEquals("15", values.get("order.timeout.minutes"));
        assertEquals("50", values.get("order.timeout.batch-size"));
    }

    @Test
    void fallsBackToDefaultsWhenYmlIsEmpty() {
        Map<String, String> values = nacos.parseValues("", Map.of(
                "order.timeout.minutes", "30", "order.timeout.batch-size", "100"));
        assertEquals("30", values.get("order.timeout.minutes"));
        assertEquals("100", values.get("order.timeout.batch-size"));
    }

    @Test
    void buildContentOnlyContainsWhitelistKeys() {
        String content = nacos.buildContent(Map.of(
                "order.timeout.minutes", "5", "order.timeout.batch-size", "20"));
        assertEquals(true, content.contains("order.timeout.minutes: 5"));
        assertEquals(true, content.contains("order.timeout.batch-size: 20"));
    }

    @Test
    void rejectsValueOutsideRange() {
        doReturn("order.timeout.minutes: 30\norder.timeout.batch-size: 100\n").when(nacos).fetchContent();
        doNothing().when(nacos).publish(anyString());

        BizException e = assertThrows(BizException.class,
                () -> service.update(Map.of("order.timeout.minutes", "9999"), "U1"));
        assertEquals(true, e.getMessage().contains("1~1440"));
        verify(nacos, never()).publish(anyString());
    }

    @Test
    void rejectsNonNumericValue() {
        doReturn("order.timeout.minutes: 30\n").when(nacos).fetchContent();
        doNothing().when(nacos).publish(anyString());

        BizException e = assertThrows(BizException.class,
                () -> service.update(Map.of("order.timeout.minutes", "abc"), "U1"));
        assertEquals(true, e.getMessage().contains("必须是整数"));
    }

    @Test
    void rejectsUnknownKey() {
        BizException e = assertThrows(BizException.class,
                () -> service.update(Map.of("some.hidden.key", "1"), "U1"));
        assertEquals(true, e.getMessage().contains("不支持修改"));
    }

    @Test
    void doesNothingWhenValueUnchanged() {
        doReturn("order.timeout.minutes: 30\norder.timeout.batch-size: 100\n").when(nacos).fetchContent();
        doNothing().when(nacos).publish(anyString());

        Map<String, String> result = service.update(Map.of("order.timeout.minutes", "30"), "U1");
        assertEquals("30", result.get("order.timeout.minutes"));
        verify(nacos, never()).publish(anyString());
        verify(auditMapper, never()).insert(anyString(), anyString(), anyString(), any(), any());
    }
}
