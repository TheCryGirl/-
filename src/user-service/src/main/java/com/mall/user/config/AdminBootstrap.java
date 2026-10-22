package com.mall.user.config;

import com.mall.user.common.IdGenerator;
import com.mall.user.common.Role;
import com.mall.user.common.UserStatus;
import com.mall.user.entity.User;
import com.mall.user.mapper.UserMapper;
import com.mall.user.util.PasswordUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.regex.Pattern;

/**
 * 初始管理员引导。
 *
 * <p>注册接口只会创建 BUYER，管理端需要一个 ADMIN 才能使用。
 * 这里根据环境变量 {@code ADMIN_BOOTSTRAP_ACCOUNT} / {@code ADMIN_BOOTSTRAP_PASSWORD}
 * 幂等地创建（或提升）一个管理员账号；未配置时不做任何事，避免把口令写进仓库。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AdminBootstrap implements ApplicationRunner {

    private static final int NICKNAME_MAX_LENGTH = 20;
    private static final int ACCOUNT_MIN_LENGTH = 4;
    private static final int ACCOUNT_MAX_LENGTH = 20;
    private static final int PASSWORD_MIN_LENGTH = 6;
    private static final int PASSWORD_MAX_LENGTH = 32;
    private static final Pattern ACCOUNT_PATTERN = Pattern.compile("^[a-zA-Z0-9_]+$");

    private final UserMapper userMapper;

    @Value("${admin.bootstrap.account:}")
    private String account;

    @Value("${admin.bootstrap.password:}")
    private String password;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (account == null || account.isBlank() || password == null || password.isBlank()) {
            log.info("未配置 ADMIN_BOOTSTRAP_ACCOUNT，跳过初始管理员引导");
            return;
        }
        String normalizedAccount = account.trim();
        // 与注册接口保持一致，避免直接写库时违反约束导致服务启动失败
        if (normalizedAccount.length() < ACCOUNT_MIN_LENGTH
                || normalizedAccount.length() > ACCOUNT_MAX_LENGTH
                || !ACCOUNT_PATTERN.matcher(normalizedAccount).matches()) {
            log.warn("ADMIN_BOOTSTRAP_ACCOUNT 需为 4-20 位字母、数字或下划线，跳过初始管理员引导");
            return;
        }
        User existing = userMapper.selectByAccount(normalizedAccount);
        if (existing != null) {
            if (existing.getRole() != Role.ADMIN) {
                userMapper.updateRoleById(existing.getUserId(), Role.ADMIN.name(), "SYSTEM");
                log.warn("已按环境变量把账号 {} 提升为管理员", normalizedAccount);
            }
            return;
        }
        if (password.length() < PASSWORD_MIN_LENGTH || password.length() > PASSWORD_MAX_LENGTH) {
            log.warn("ADMIN_BOOTSTRAP_PASSWORD 长度需为 {}-{} 位，跳过初始管理员创建",
                    PASSWORD_MIN_LENGTH, PASSWORD_MAX_LENGTH);
            return;
        }

        String nickname = buildNickname(normalizedAccount);
        if (userMapper.selectByNickname(nickname) != null) {
            log.warn("昵称 {} 已被占用，跳过初始管理员创建，请更换 ADMIN_BOOTSTRAP_ACCOUNT", nickname);
            return;
        }

        User admin = new User();
        admin.setUserId(IdGenerator.userId());
        admin.setAccount(normalizedAccount);
        admin.setPasswordHash(PasswordUtil.hash(password));
        admin.setNickname(nickname);
        admin.setRole(Role.ADMIN);
        admin.setStatus(UserStatus.ACTIVE);
        admin.setCreatedAt(LocalDateTime.now());
        admin.setUpdatedAt(LocalDateTime.now());
        admin.setCreatedBy("SYSTEM");
        admin.setUpdatedBy("SYSTEM");
        admin.setIsDeleted(Boolean.FALSE);
        userMapper.insert(admin);
        log.warn("已按环境变量创建初始管理员账号: {}", normalizedAccount);
    }

    /** 昵称需要全局唯一且不超过 20 位，账号本身最多 20 位 */
    private String buildNickname(String normalizedAccount) {
        String nickname = "管理员" + normalizedAccount;
        return nickname.length() <= NICKNAME_MAX_LENGTH
                ? nickname
                : nickname.substring(0, NICKNAME_MAX_LENGTH);
    }
}
