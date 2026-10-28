package com.mall.user.service;

import com.mall.user.common.BizException;
import com.mall.user.common.ErrorCode;
import com.mall.user.dto.response.AvatarUploadResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/**
 * 头像存储。
 *
 * <p>与商品图片一致的方案（契约 9.4）：本地目录 + 静态资源映射。
 * 上传后返回站内相对路径 {@code /avatars/xxx.png}，由网关转发到 user-service。
 */
@Slf4j
@Service
public class AvatarStorageService {

    private static final Set<String> IMAGE_EXTENSIONS = Set.of("jpg", "jpeg", "png", "webp", "gif");
    private static final long MAX_SIZE = 5L * 1024 * 1024;

    @Value("${user.upload-dir:uploads}")
    private String uploadDir;

    public AvatarUploadResponse store(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BizException(ErrorCode.PARAM_INVALID, "头像文件不能为空");
        }
        if (file.getSize() > MAX_SIZE) {
            throw new BizException(ErrorCode.PARAM_INVALID, "头像不能超过 5MB");
        }
        String originalName = file.getOriginalFilename();
        String extension = originalName == null || !originalName.contains(".")
                ? "" : originalName.substring(originalName.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT);
        if (!IMAGE_EXTENSIONS.contains(extension)) {
            throw new BizException(ErrorCode.PARAM_INVALID, "只支持 jpg/jpeg/png/webp/gif 图片");
        }

        // 文件名用 UUID，避免同名覆盖与路径穿越
        String filename = UUID.randomUUID().toString().replace("-", "") + "." + extension;
        try {
            Path directory = Paths.get(uploadDir, "avatars").toAbsolutePath().normalize();
            Files.createDirectories(directory);
            Path target = directory.resolve(filename).normalize();
            if (!target.startsWith(directory)) {
                throw new BizException(ErrorCode.PARAM_INVALID, "文件名不合法");
            }
            try (InputStream inputStream = file.getInputStream()) {
                Files.copy(inputStream, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            log.error("头像保存失败, uploadDir={}", uploadDir, e);
            throw new BizException(ErrorCode.SERVICE_UNAVAILABLE, "头像保存失败，请稍后重试");
        }
        return new AvatarUploadResponse("/avatars/" + filename);
    }
}
