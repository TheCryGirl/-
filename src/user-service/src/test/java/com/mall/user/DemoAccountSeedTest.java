package com.mall.user;

import com.mall.user.util.PasswordUtil;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.FileSystemResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceUtils;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import javax.sql.DataSource;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Connection;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 演示账号种子脚本（{@code src/docker/seed-demo-accounts.sql}）的可用性检查。
 *
 * <p>这个脚本是别人拉起项目后唯一的登录入口，所以两件事必须钉死：
 * 脚本里的 BCrypt 哈希确实对应文档写的演示口令，且重复执行不会造出重复账号。
 * 测试在事务内执行并回滚，不污染其它用例。
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class DemoAccountSeedTest {

    private static final String DEMO_PASSWORD = "Demo12345";

    @Autowired
    private DataSource dataSource;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void seedCreatesFiveLoginableAccountsAndIsIdempotent() throws Exception {
        runSeed();
        runSeed();

        List<String> accounts = jdbcTemplate.queryForList(
                "SELECT account FROM `user` WHERE user_id LIKE 'U-DEMO-%' ORDER BY user_id", String.class);
        assertThat(accounts).containsExactly(
                "demo_buyer", "demo_buyer2", "demo_seller", "demo_seller2", "demo_admin");

        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM shop WHERE shop_id LIKE 'S-DEMO-%'", Integer.class)).isEqualTo(2);

        // 角色与营业状态决定了每个账号能演示什么，改动前先看契约
        assertThat(jdbcTemplate.queryForList(
                "SELECT CONCAT(role, '/', status) FROM `user` WHERE user_id LIKE 'U-DEMO-%' ORDER BY user_id",
                String.class)).containsExactly(
                "BUYER/ACTIVE", "BUYER/ACTIVE", "SELLER/ACTIVE", "SELLER/ACTIVE", "ADMIN/ACTIVE");
        assertThat(jdbcTemplate.queryForList(
                "SELECT CONCAT(apply_status, '/', status, '/', deposit_amount) FROM shop "
                        + "WHERE shop_id LIKE 'S-DEMO-%' ORDER BY shop_id", String.class)).containsExactly(
                "APPROVED/ACTIVE/100000", "APPROVED/ACTIVE/100000");

        // 口令能真正登录：5 个哈希必须都能与文档里的演示口令匹配
        for (String hash : jdbcTemplate.queryForList(
                "SELECT password_hash FROM `user` WHERE user_id LIKE 'U-DEMO-%'", String.class)) {
            assertThat(PasswordUtil.matches(DEMO_PASSWORD, hash))
                    .as("演示口令必须与种子哈希匹配").isTrue();
            assertThat(PasswordUtil.matches(DEMO_PASSWORD + "x", hash)).isFalse();
        }
    }

    private void runSeed() throws Exception {
        Connection connection = DataSourceUtils.getConnection(dataSource);
        try {
            ScriptUtils.executeSqlScript(connection, new FileSystemResource(seedScript()));
        } finally {
            DataSourceUtils.releaseConnection(connection, dataSource);
        }
    }

    /** Maven 以模块目录为工作目录运行；IDE 可能以仓库根目录运行，两种都认。 */
    private static Path seedScript() {
        for (String candidate : List.of("../docker/seed-demo-accounts.sql", "src/docker/seed-demo-accounts.sql")) {
            Path path = Paths.get(candidate).toAbsolutePath().normalize();
            if (Files.isRegularFile(path)) {
                return path;
            }
        }
        throw new IllegalStateException("未找到演示账号种子脚本 src/docker/seed-demo-accounts.sql");
    }
}
