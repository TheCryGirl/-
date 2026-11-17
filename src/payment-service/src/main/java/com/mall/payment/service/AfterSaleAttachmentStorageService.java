package com.mall.payment.service;

import com.mall.payment.common.BizException;
import com.mall.payment.common.ErrorCode;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * 售后凭证存储（#143）。
 *
 * <p>复用头像/商品图片同一套方案（契约 9.4）：本地目录 + 静态资源映射。
 * 限 5MB、格式白名单、UUID 重命名防覆盖与路径穿越，落盘后返回站内相对路径
 * {@code /after-sale/xxx.png}，由网关原样转发到 payment-service 静态映射。
 *
 * <p>采用「先上传后引用」范式：上传只落盘并返回路径，创建/补充售后单时再校验
 * 路径合法性并绑定到工单，与商品图、头像一致。
 */
@Slf4j
@Service
public class AfterSaleAttachmentStorageService {

    private static final Set<String> IMAGE_EXTENSIONS = Set.of("jpg", "jpeg", "png", "webp", "gif");
    private static final long MAX_SIZE = 5L * 1024 * 1024;
    private static final int MAX_FILES = 3;
    /** 只接受本服务生成的 UUID 文件名，杜绝绑定任意路径 */
    private static final Pattern URL_PATTERN =
            Pattern.compile("^/after-sale/[0-9a-f]{32}\\.(jpg|jpeg|png|webp|gif)$");

    @Value("${payment.upload-dir:uploads}")
    private String uploadDir;

    /** 单次最多 3 张，逐张校验大小与格式，返回站内路径列表 */
    public List<String> storeAll(List<MultipartFile> files) {
        if (files == null || files.isEmpty()) {
            throw new BizException(ErrorCode.PARAM_INVALID, "凭证文件不能为空");
        }
        if (files.size() > MAX_FILES) {
            throw new BizException(ErrorCode.PARAM_INVALID, "单次最多上传 3 张凭证");
        }
        List<String> urls = new ArrayList<>(files.size());
        for (MultipartFile file : files) {
            urls.add(store(file));
        }
        return urls;
    }

    public String store(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BizException(ErrorCode.PARAM_INVALID, "凭证文件不能为空");
        }
        if (file.getSize() > MAX_SIZE) {
            throw new BizException(ErrorCode.PARAM_INVALID, "凭证不能超过 5MB");
        }
        String originalName = file.getOriginalFilename();
        String extension = originalName == null || !originalName.contains(".")
                ? "" : originalName.substring(originalName.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT);
        if (!IMAGE_EXTENSIONS.contains(extension)) {
            throw new BizException(ErrorCode.PARAM_INVALID, "只支持 jpg/jpeg/png/webp/gif 图片");
        }

        String filename = UUID.randomUUID().toString().replace("-", "") + "." + extension;
        try {
            Path directory = Paths.get(uploadDir, "after-sale").toAbsolutePath().normalize();
            Files.createDirectories(directory);
            Path target = directory.resolve(filename).normalize();
            if (!target.startsWith(directory)) {
                throw new BizException(ErrorCode.PARAM_INVALID, "文件名不合法");
            }
            try (InputStream inputStream = file.getInputStream()) {
                Files.copy(inputStream, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            log.error("售后凭证保存失败, uploadDir={}", uploadDir, e);
            throw new BizException(ErrorCode.SERVICE_UNAVAILABLE, "凭证保存失败，请稍后重试");
        }
        return "/after-sale/" + filename;
    }

    /** 绑定前校验：数量上限 + 路径必须是本服务生成的站内凭证路径 */
    public void validateUrls(List<String> urls) {
        if (urls == null || urls.isEmpty()) {
            return;
        }
        if (urls.size() > MAX_FILES) {
            throw new BizException(ErrorCode.PARAM_INVALID, "单次最多上传 3 张凭证");
        }
        for (String url : urls) {
            if (url == null || !URL_PATTERN.matcher(url).matches()) {
                throw new BizException(ErrorCode.PARAM_INVALID, "凭证路径不合法");
            }
        }
    }
}