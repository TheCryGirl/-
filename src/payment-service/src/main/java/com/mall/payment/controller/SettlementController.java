package com.mall.payment.controller;

import com.mall.payment.common.BizException;
import com.mall.payment.common.ErrorCode;
import com.mall.payment.common.PageResult;
import com.mall.payment.common.Result;
import com.mall.payment.dto.request.GenerateSettlementRequest;
import com.mall.payment.dto.request.SaveCommissionRuleRequest;
import com.mall.payment.entity.CommissionRule;
import java.util.List;
import com.mall.payment.dto.request.UpdateSettlementStatusRequest;
import com.mall.payment.dto.response.SettlementGenerateResponse;
import com.mall.payment.dto.response.SettlementResponse;
import com.mall.payment.service.SettlementService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 佣金结算接口（#144 第二块）。
 *
 * <p>卖家看自己的结算单，管理员看全部并负责生成与标记打款；
 * 身份只认 X-User-Id / X-User-Role 请求头，不接受 query 参数授权。
 */
@RestController
@RequestMapping({"/settlements", "/api/settlements"})
@RequiredArgsConstructor
public class SettlementController {

    private static final String ROLE_ADMIN = "ADMIN";
    private static final String ROLE_SELLER = "SELLER";

    private final SettlementService settlementService;

    @GetMapping
    public Result<PageResult<SettlementResponse>> list(
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @RequestHeader(value = "X-User-Role", required = false) String role,
            @RequestParam(required = false) String sellerId,
            @RequestParam(required = false) String period,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        if (userId == null || userId.isBlank()) {
            throw new BizException(ErrorCode.UNAUTHORIZED, "未登录或用户信息缺失");
        }
        if (ROLE_ADMIN.equals(role)) {
            return Result.success(settlementService.list(sellerId, period, page, size));
        }
        if (ROLE_SELLER.equals(role)) {
            if (sellerId != null && !sellerId.isBlank() && !sellerId.equals(userId)) {
                throw new BizException(ErrorCode.FORBIDDEN, "卖家只能查看自己的结算单");
            }
            return Result.success(settlementService.listForSeller(userId, period, page, size));
        }
        throw new BizException(ErrorCode.FORBIDDEN, "仅卖家或管理员可查看结算单");
    }

    @GetMapping("/commission-rules")
    public Result<List<CommissionRule>> commissionRules(
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @RequestHeader(value = "X-User-Role", required = false) String role) {
        requireAdmin(userId, role);
        return Result.success(settlementService.commissionRules());
    }

    @PutMapping("/commission-rules")
    public Result<Void> saveCommissionRule(
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @RequestHeader(value = "X-User-Role", required = false) String role,
            @RequestBody SaveCommissionRuleRequest request) {
        requireAdmin(userId, role);
        settlementService.saveCommissionRule(request.scope(), request.targetId(), request.rate(), userId);
        return Result.success(null);
    }

    @PostMapping("/generate")
    public Result<SettlementGenerateResponse> generate(
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @RequestHeader(value = "X-User-Role", required = false) String role,
            @RequestBody(required = false) GenerateSettlementRequest request) {
        requireAdmin(userId, role);
        String period = request == null ? null : request.getPeriod();
        return Result.success(settlementService.generate(period, userId));
    }

    @PutMapping("/{settlementId}/status")
    public Result<SettlementResponse> updateStatus(
            @PathVariable("settlementId") String settlementId,
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @RequestHeader(value = "X-User-Role", required = false) String role,
            @Valid @RequestBody UpdateSettlementStatusRequest request) {
        requireAdmin(userId, role);
        return Result.success(settlementService.markPaid(settlementId, request.getStatus(), userId,
                request.getExpectedNetAmount(), request.getPayoutReference(), request.getPayoutRemark()));
    }

    private void requireAdmin(String userId, String role) {
        if (!ROLE_ADMIN.equals(role)) {
            throw new BizException(ErrorCode.FORBIDDEN, "仅管理员可操作结算");
        }
        if (userId == null || userId.isBlank()) {
            throw new BizException(ErrorCode.UNAUTHORIZED, "未登录或用户信息缺失");
        }
    }
}
