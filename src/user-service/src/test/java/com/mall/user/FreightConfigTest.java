package com.mall.user;

import com.mall.user.common.BizException;
import com.mall.user.common.ErrorCode;
import com.mall.user.dto.request.FreightConfigRequest;
import com.mall.user.dto.request.FreightRuleRequest;
import com.mall.user.dto.response.FreightConfigResponse;
import com.mall.user.service.FreightService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 运费模板配置（#136）：卖家保存 / 读取、整体覆盖、重复档位校验，以及给 order-service 的内部查询。
 */
@SpringBootTest
@ActiveProfiles("test")
class FreightConfigTest {

    @Autowired
    private FreightService freightService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void clean() {
        jdbcTemplate.update("DELETE FROM freight_rule");
        jdbcTemplate.update("DELETE FROM freight_template");
        jdbcTemplate.update("DELETE FROM shop");
        jdbcTemplate.update("DELETE FROM `user`");
    }

    @Test
    void sellerSavesAndReadsBackFreightConfig() {
        String sellerId = createSeller("U136-SELLER");

        FreightConfigResponse saved = freightService.saveMine(sellerId,
                request("全国运费", 9900L, rule("DEFAULT", 0L, 600L), rule("河北省", 0L, 800L)));

        assertThat(saved.getTemplateId()).isNotBlank();
        assertThat(saved.getFreeThreshold()).isEqualTo(9900L);
        assertThat(saved.getRules()).hasSize(2);
        // 卖家读取到的与保存的一致
        FreightConfigResponse mine = freightService.getMine(sellerId);
        assertThat(mine.getTemplateId()).isEqualTo(saved.getTemplateId());
        assertThat(mine.getName()).isEqualTo("全国运费");
        assertThat(mine.getRules()).extracting(FreightConfigResponse.FreightRuleResponse::getRegionCode)
                .containsExactly("DEFAULT", "河北省");
        // 内部接口按卖家同样能取到
        FreightConfigResponse bySeller = freightService.getBySellerId(sellerId);
        assertThat(bySeller.getFreeThreshold()).isEqualTo(9900L);
        assertThat(bySeller.getTemplateId()).isEqualTo(saved.getTemplateId());
    }

    @Test
    void secondSaveOverwritesRulesAndKeepsTemplateId() {
        String sellerId = createSeller("U136-SELLER-2");

        FreightConfigResponse first = freightService.saveMine(sellerId,
                request("模板A", 0L, rule("DEFAULT", 0L, 500L), rule("河北省", 0L, 800L)));
        FreightConfigResponse second = freightService.saveMine(sellerId,
                request("模板B", 12000L, rule("天津市", 0L, 700L)));

        assertThat(second.getTemplateId()).isEqualTo(first.getTemplateId());
        assertThat(second.getName()).isEqualTo("模板B");
        assertThat(second.getFreeThreshold()).isEqualTo(12000L);
        assertThat(second.getRules()).hasSize(1);
        assertThat(second.getRules().get(0).getRegionCode()).isEqualTo("天津市");
        Integer totalRules = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM freight_rule WHERE template_id = ? AND is_deleted = 0",
                Integer.class, first.getTemplateId());
        assertThat(totalRules).isEqualTo(1);
    }

    @Test
    void duplicateRegionAndTierIsRejected() {
        String sellerId = createSeller("U136-SELLER-3");

        assertThatThrownBy(() -> freightService.saveMine(sellerId, request("重复档位", 0L,
                rule("河北省", 5000L, 300L), rule("河北省", 5000L, 200L))))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("同一地区")
                .extracting(e -> ((BizException) e).getCode())
                .isEqualTo(ErrorCode.PARAM_INVALID);
    }

    @Test
    void unknownSellerReadsAsUnconfiguredInsteadOfFailing() {
        FreightConfigResponse response = freightService.getBySellerId("U-NOT-EXISTS");

        assertThat(response.getTemplateId()).isNull();
        assertThat(response.getFreeThreshold()).isZero();
        assertThat(response.getRules()).isEmpty();
    }

    /* ---------------- 造数 ---------------- */

    /**
     * 建一个可配置运费的卖家：先落用户，再落一条「存量店铺」。
     *
     * <p>#144 之后开店必须走入驻审核，`ShopService.getMine` 不再自动建店，
     * 因此运费用例不能再依赖「第一次访问自动建店」，必须自己准备店铺记录。
     * 存量店铺按已通过审核（`APPROVED`）落库，对应 #144 验收里的历史店铺口径。
     */
    private String createSeller(String sellerId) {
        Timestamp now = Timestamp.valueOf(LocalDateTime.now());
        jdbcTemplate.update("""
                INSERT INTO `user`(user_id, account, password_hash, nickname, phone, role, status,
                                   created_at, updated_at, is_deleted)
                VALUES (?, ?, 'hash', ?, '13800138000', 'SELLER', 'ACTIVE', ?, ?, 0)
                """, sellerId, "acct-" + sellerId, "昵称" + sellerId, now, now);
        insertLegacyShop(sellerId, now);
        return sellerId;
    }

    /**
     * 存量店铺（已通过入驻审核）。
     *
     * <p>#144 给 shop 加了 `apply_status` / `deposit_amount`，而这两个列在 #144 合并前不存在。
     * 本用例按实际列名决定是否写入，保证同一份测试在 #144 前后都能跑：
     * #144 合并后可以在本方法里删掉 else 分支。
     */
    private void insertLegacyShop(String sellerId, Timestamp now) {
        String shopId = "S-" + sellerId;
        String name = "店铺" + sellerId;
        if (hasColumn("SHOP", "APPLY_STATUS")) {
            jdbcTemplate.update("""
                    INSERT INTO shop(shop_id, seller_id, name, status, apply_status, deposit_amount,
                                     created_at, updated_at, is_deleted)
                    VALUES (?, ?, ?, 'ACTIVE', 'APPROVED', 0, ?, ?, 0)
                    """, shopId, sellerId, name, now, now);
            return;
        }
        jdbcTemplate.update("""
                INSERT INTO shop(shop_id, seller_id, name, status, created_at, updated_at, is_deleted)
                VALUES (?, ?, ?, 'ACTIVE', ?, ?, 0)
                """, shopId, sellerId, name, now, now);
    }

    /** 表/列是否存在：H2 的 information_schema 用大写存放未加引号的标识符。 */
    private boolean hasColumn(String table, String column) {
        Integer count = jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
                WHERE UPPER(TABLE_NAME) = ? AND UPPER(COLUMN_NAME) = ?
                """, Integer.class, table, column);
        return count != null && count > 0;
    }

    private static FreightConfigRequest request(String name, long freeThreshold, FreightRuleRequest... rules) {
        FreightConfigRequest request = new FreightConfigRequest();
        request.setName(name);
        request.setFreeThreshold(freeThreshold);
        request.setRules(List.of(rules));
        return request;
    }

    private static FreightRuleRequest rule(String region, long minAmount, long fee) {
        FreightRuleRequest rule = new FreightRuleRequest();
        rule.setRegionCode(region);
        rule.setMinAmount(minAmount);
        rule.setFee(fee);
        return rule;
    }
}
