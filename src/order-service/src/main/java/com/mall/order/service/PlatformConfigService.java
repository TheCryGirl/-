package com.mall.order.service;

import com.mall.order.common.BizException;
import com.mall.order.common.ErrorCode;
import com.mall.order.entity.ConfigAudit;
import com.mall.order.mapper.ConfigAuditMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 平台配置读写编排（任务 #43）。
 *
 * <p>流程：读当前 Nacos 配置 → 校验新值 → 合并白名单键 → 发布 → 逐键写变更留痕。
 * 只允许改白名单里的键，避免管理端写入任意配置把服务搞坏。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PlatformConfigService {

    /** 各键的默认值，用于配置中心尚未发布过该键时的兜底 */
    private static final Map<String, String> DEFAULTS = Map.of(
            "order.timeout.minutes", "30",
            "order.timeout.batch-size", "100");

    private final NacosConfigService nacosConfigService;
    private final ConfigAuditMapper configAuditMapper;

    /**
     * 取单个键的当前生效值，供业务代码实时读取。
     *
     * <p>每次都回源配置中心（带 5 秒超时），所以管理端改完立刻生效；
     * 读失败时退回默认值，不影响下单主流程。
     */
    public long currentLong(String key) {
        try {
            String v = current().get(key);
            return Long.parseLong(v);
        } catch (Exception e) {
            log.warn("读取配置 {} 失败，使用默认值 {}", key, DEFAULTS.get(key));
            return Long.parseLong(DEFAULTS.getOrDefault(key, "30"));
        }
    }

    /** 查当前生效值 */
    public Map<String, String> current() {
        String yml = nacosConfigService.fetchContent();
        Map<String, String> values = nacosConfigService.parseValues(yml, DEFAULTS);
        Map<String, String> result = new LinkedHashMap<>();
        values.forEach((k, v) -> result.put(k, v));
        return result;
    }

    /** 可供管理端展示的键说明 */
    public Map<String, String> keyLabels() {
        return new LinkedHashMap<>(NacosConfigService.WHITELIST);
    }

    /**
     * 更新配置。只处理白名单键，未传的键保持原值。
     *
     * @return 更新后的全部白名单键值
     */
    public Map<String, String> update(Map<String, String> changes, String operatorId) {
        if (changes == null || changes.isEmpty()) {
            throw new BizException(ErrorCode.PARAM_INVALID, "没有需要修改的配置项");
        }
        // 先做纯本地校验（键白名单 + 取值范围），任何非法请求都不必去连配置中心
        for (Map.Entry<String, String> e : changes.entrySet()) {
            if (!NacosConfigService.WHITELIST.containsKey(e.getKey())) {
                throw new BizException(ErrorCode.PARAM_INVALID, "不支持修改该配置项：" + e.getKey());
            }
            validate(e.getKey(), e.getValue());
        }

        Map<String, String> current = current();
        Map<String, String> merged = new LinkedHashMap<>(current);
        Map<String, String[]> audit = new LinkedHashMap<>();

        for (Map.Entry<String, String> e : changes.entrySet()) {
            String key = e.getKey();
            String newValue = validate(key, e.getValue());
            String oldValue = current.get(key);
            if (!newValue.equals(oldValue)) {
                audit.put(key, new String[]{oldValue, newValue});
            }
            merged.put(key, newValue);
        }

        if (audit.isEmpty()) {
            return merged;
        }

        nacosConfigService.publish(nacosConfigService.buildContent(merged));

        for (Map.Entry<String, String[]> e : audit.entrySet()) {
            configAuditMapper.insert(operatorId, NacosConfigService.DATA_ID,
                    e.getKey(), e.getValue()[0], e.getValue()[1]);
        }
        log.info("平台配置已更新, operator={}, keys={}", operatorId, audit.keySet());
        return merged;
    }

    /** 最近变更记录 */
    public List<ConfigAudit> history(int limit) {
        int safe = Math.min(Math.max(limit, 1), 100);
        return configAuditMapper.selectRecent(safe);
    }

    private String validate(String key, String raw) {
        if (raw == null || raw.isBlank()) {
            throw new BizException(ErrorCode.PARAM_INVALID, key + " 不能为空");
        }
        int value;
        try {
            value = Integer.parseInt(raw.trim());
        } catch (NumberFormatException e) {
            throw new BizException(ErrorCode.PARAM_INVALID, key + " 必须是整数");
        }
        if ("order.timeout.minutes".equals(key) && (value < 1 || value > 1440)) {
            throw new BizException(ErrorCode.PARAM_INVALID, "订单超时分钟数需在 1~1440 之间");
        }
        if ("order.timeout.batch-size".equals(key) && (value < 1 || value > 500)) {
            throw new BizException(ErrorCode.PARAM_INVALID, "扫描批量需在 1~500 之间");
        }
        return String.valueOf(value);
    }
}
