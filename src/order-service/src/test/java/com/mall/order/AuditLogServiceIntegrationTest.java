package com.mall.order;

import com.mall.order.common.BizException;
import com.mall.order.common.ErrorCode;
import com.mall.order.common.PageResult;
import com.mall.order.dto.request.AuditLogRequest;
import com.mall.order.entity.AuditLog;
import com.mall.order.service.AuditLogService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** 操作审计日志（任务 #58）的集成测试。 */
@SpringBootTest
@ActiveProfiles("test")
class AuditLogServiceIntegrationTest {

    @Autowired
    private AuditLogService auditLogService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() {
        jdbcTemplate.update("DELETE FROM audit_log");
    }

    @Test
    void recordsAndFiltersByOperatorAndAction() {
        auditLogService.record("U1", "ADMIN", "PRODUCT_AUDIT", "PRODUCT", "P1", "审核通过");
        auditLogService.record("U1", "ADMIN", "USER_STATUS", "USER", "U2", "冻结用户");
        auditLogService.record("U9", "ADMIN", "PRODUCT_AUDIT", "PRODUCT", "P2", "审核驳回");

        PageResult<AuditLog> byOperator = auditLogService.list("U1", null, null, null, 1, 20);
        assertThat(byOperator.total()).isEqualTo(2);

        PageResult<AuditLog> byAction = auditLogService.list(null, "PRODUCT_AUDIT", null, null, 1, 20);
        assertThat(byAction.total()).isEqualTo(2);
        assertThat(byAction.list()).extracting(AuditLog::getTargetId).containsExactlyInAnyOrder("P1", "P2");

        PageResult<AuditLog> both = auditLogService.list("U1", "PRODUCT_AUDIT", null, null, 1, 20);
        assertThat(both.total()).isEqualTo(1);
        assertThat(both.list().get(0).getResult()).isEqualTo("SUCCESS");
    }

    @Test
    void paginatesNewestFirst() {
        for (int i = 1; i <= 25; i++) {
            auditLogService.record("U1", "ADMIN", "CONFIG_UPDATE", "CONFIG", "mall-platform.yml", "第 " + i + " 次");
        }
        PageResult<AuditLog> firstPage = auditLogService.list(null, "CONFIG_UPDATE", null, null, 1, 10);
        assertThat(firstPage.total()).isEqualTo(25);
        assertThat(firstPage.list()).hasSize(10);
        assertThat(firstPage.list().get(0).getDetail()).isEqualTo("第 25 次");
        assertThat(firstPage.pages()).isEqualTo(3);

        PageResult<AuditLog> lastPage = auditLogService.list(null, "CONFIG_UPDATE", null, null, 3, 10);
        assertThat(lastPage.list()).hasSize(5);
        assertThat(lastPage.list().get(4).getDetail()).isEqualTo("第 1 次");
    }

    @Test
    void supportsDateRangeFilter() {
        auditLogService.record("U1", "ADMIN", "USER_ROLE", "USER", "U2", "设置卖家");
        String today = java.time.LocalDate.now().toString();
        assertThat(auditLogService.list(null, null, today, today, 1, 10).total()).isEqualTo(1);
        assertThat(auditLogService.list(null, null, "2000-01-01", "2000-01-02", 1, 10).total()).isZero();
    }

    @Test
    void rejectsMissingOperatorAndInvalidResult() {
        AuditLogRequest missingOperator = new AuditLogRequest();
        missingOperator.setAction("PRODUCT_AUDIT");
        assertThatThrownBy(() -> auditLogService.record(missingOperator))
                .isInstanceOf(BizException.class)
                .extracting(e -> ((BizException) e).getCode())
                .isEqualTo(ErrorCode.PARAM_INVALID);

        AuditLogRequest invalidResult = new AuditLogRequest();
        invalidResult.setOperatorId("U1");
        invalidResult.setAction("PRODUCT_AUDIT");
        invalidResult.setResult("MAYBE");
        assertThatThrownBy(() -> auditLogService.record(invalidResult))
                .isInstanceOf(BizException.class)
                .extracting(e -> ((BizException) e).getCode())
                .isEqualTo(ErrorCode.PARAM_INVALID);
    }
}
