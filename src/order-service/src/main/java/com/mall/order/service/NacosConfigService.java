package com.mall.order.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.StringJoiner;

/**
 * 通过 Nacos Open API 读写平台配置（任务 #43）。
 *
 * <p>只托管 order-service 真正在用的两个可调参数，dataId 固定为 {@code mall-platform.yml}。
 * 这里故意不引入 WebClient：order-service 是 Servlet 应用，再加 webflux 会形成双 web 栈，
 * 用 JDK 自带的 HttpClient 就够了。
 */
@Slf4j
@Service
public class NacosConfigService {

    /** 配置中心里的 dataId，与 application.yaml 的 spring.config.import 保持一致 */
    public static final String DATA_ID = "mall-platform.yml";
    public static final String GROUP = "DEFAULT_GROUP";

    /** 允许从管理端修改的键白名单，顺序即展示顺序 */
    public static final Map<String, String> WHITELIST = new LinkedHashMap<>() {{
        put("order.timeout.minutes", "订单支付超时（分钟）");
        put("order.timeout.batch-size", "超时扫描单批条数");
    }};

    private static final Duration TIMEOUT = Duration.ofSeconds(5);

    private final HttpClient http = HttpClient.newBuilder().connectTimeout(TIMEOUT).build();

    @Value("${platform.nacos-config-url:http://127.0.0.1:8848}")
    private String nacosAddr;

    @Value("${platform.nacos-username:nacos}")
    private String username;

    @Value("${platform.nacos-password:nacos}")
    private String password;

    /** 读取当前配置内容；配置不存在时返回空串 */
    public String fetchContent() {
        try {
            String token = login();
            String url = nacosAddr + "/nacos/v1/cs/configs?dataId=" + enc(DATA_ID)
                    + "&group=" + enc(GROUP) + (token == null ? "" : "&accessToken=" + enc(token));
            HttpResponse<String> resp = http.send(
                    HttpRequest.newBuilder(URI.create(url)).timeout(TIMEOUT).GET().build(),
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (resp.statusCode() == 200) {
                return resp.body();
            }
            // 404 表示尚未发布过该配置，视为空
            if (resp.statusCode() == 404) {
                return "";
            }
            throw new IllegalStateException("读取 Nacos 配置失败, status=" + resp.statusCode());
        } catch (Exception e) {
            log.error("读取 Nacos 配置异常, dataId={}", DATA_ID, e);
            throw new IllegalStateException("配置中心不可用：" + e.getMessage(), e);
        }
    }

    /** 把整份 yml 内容发布到配置中心，发布成功后各服务会收到推送 */
    public void publish(String content) {
        try {
            String token = login();
            String form = "dataId=" + enc(DATA_ID)
                    + "&group=" + enc(GROUP)
                    + "&type=yaml"
                    + "&content=" + enc(content)
                    + (token == null ? "" : "&accessToken=" + enc(token));
            HttpResponse<String> resp = http.send(
                    HttpRequest.newBuilder(URI.create(nacosAddr + "/nacos/v1/cs/configs"))
                            .timeout(TIMEOUT)
                            .header("Content-Type", "application/x-www-form-urlencoded")
                            .POST(HttpRequest.BodyPublishers.ofString(form, StandardCharsets.UTF_8))
                            .build(),
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (resp.statusCode() != 200 || !resp.body().trim().equalsIgnoreCase("true")) {
                throw new IllegalStateException("发布 Nacos 配置失败, status=" + resp.statusCode() + ", body=" + resp.body());
            }
            log.info("Nacos 配置发布成功, dataId={}", DATA_ID);
        } catch (IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            log.error("发布 Nacos 配置异常, dataId={}", DATA_ID, e);
            throw new IllegalStateException("配置中心不可用：" + e.getMessage(), e);
        }
    }

    /** 登录拿 accessToken；集群未开启鉴权时返回 null，直接匿名访问 */
    private String login() {
        if (username == null || username.isBlank()) {
            return null;
        }
        try {
            String form = "username=" + enc(username) + "&password=" + enc(password);
            HttpResponse<String> resp = http.send(
                    HttpRequest.newBuilder(URI.create(nacosAddr + "/nacos/v1/auth/users/login"))
                            .timeout(TIMEOUT)
                            .header("Content-Type", "application/x-www-form-urlencoded")
                            .POST(HttpRequest.BodyPublishers.ofString(form, StandardCharsets.UTF_8))
                            .build(),
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (resp.statusCode() != 200) {
                // 鉴权未开启时该接口可能 404/403，退回匿名访问
                log.debug("Nacos 登录未成功（可能未开启鉴权）, status={}", resp.statusCode());
                return null;
            }
            String body = resp.body();
            int idx = body.indexOf("\"accessToken\":\"");
            if (idx < 0) {
                return null;
            }
            int start = idx + "\"accessToken\":\"".length();
            int end = body.indexOf('"', start);
            return end > start ? body.substring(start, end) : null;
        } catch (Exception e) {
            log.debug("Nacos 登录异常，按匿名访问处理: {}", e.getMessage());
            return null;
        }
    }

    /** 解析 yml 中白名单键的当前值；缺失时用 defaultValue */
    public Map<String, String> parseValues(String yml, Map<String, String> defaults) {
        Map<String, String> result = new LinkedHashMap<>();
        for (String key : WHITELIST.keySet()) {
            result.put(key, extract(yml, key, defaults.getOrDefault(key, "")));
        }
        return result;
    }

    /** 由当前 yml 生成只含白名单键的新 yml 文本 */
    public String buildContent(Map<String, String> values) {
        StringJoiner sj = new StringJoiner("\n");
        sj.add("# 平台配置，由管理端「系统配置」维护，改动即时推送到各服务");
        for (String key : WHITELIST.keySet()) {
            sj.add(key + ": " + values.get(key));
        }
        return sj.toString() + "\n";
    }

    private static String extract(String yml, String key, String fallback) {
        if (yml == null || yml.isBlank()) {
            return fallback;
        }
        for (String line : yml.split("\\R")) {
            String t = line.trim();
            if (t.startsWith("#")) {
                continue;
            }
            if (t.startsWith(key + ":")) {
                String v = t.substring((key + ":").length()).trim();
                if (!v.isEmpty()) {
                    return v;
                }
            }
        }
        return fallback;
    }

    private static String enc(String v) {
        return URLEncoder.encode(v, StandardCharsets.UTF_8);
    }
}
