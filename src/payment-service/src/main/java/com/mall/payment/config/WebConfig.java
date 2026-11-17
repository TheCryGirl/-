package com.mall.payment.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.nio.file.Paths;

/**
 * 售后凭证静态资源映射（#143）。
 *
 * <p>与头像、商品图片同一套方案（契约 9.4）：本地目录 + 静态映射。
 * 网关把 {@code /after-sale/**} 原样转发到本服务，浏览器可直接用 img src 预览。
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Value("${payment.upload-dir:uploads}")
    private String uploadDir;

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        String location = Paths.get(uploadDir, "after-sale").toAbsolutePath().normalize().toUri().toString();
        if (!location.endsWith("/")) {
            location = location + "/";
        }
        registry.addResourceHandler("/after-sale/**").addResourceLocations(location);
    }
}