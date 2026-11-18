package com.mall.user.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mall.user.client.ProductClient;
import com.mall.user.client.dto.SellerVisibilityRequest;
import com.mall.user.common.BizException;
import com.mall.user.common.ErrorCode;
import com.mall.user.common.IdGenerator;
import com.mall.user.common.Result;
import com.mall.user.common.ShopApplyStatus;
import com.mall.user.common.ShopStatus;
import com.mall.user.dto.request.ShopApplyRequest;
import com.mall.user.dto.request.ShopAuditRequest;
import com.mall.user.dto.request.ShopRequest;
import com.mall.user.dto.request.ShopStatusRequest;
import com.mall.user.dto.response.PageResponse;
import com.mall.user.dto.response.ShopResponse;
import com.mall.user.entity.Shop;
import com.mall.user.entity.ShopApplyLog;
import com.mall.user.entity.User;
import com.mall.user.mapper.ShopApplyLogMapper;
import com.mall.user.mapper.ShopMapper;
import com.mall.user.mapper.UserMapper;
import com.mall.user.service.ShopService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * 店铺资料、入驻审核与营业状态服务。
 *
 * <p>营业状态（ACTIVE / SUSPENDED）仍是 product-service 与 order-service 唯一的拦截依据（#49），
 * 入驻审核不新增跨服务调用：未过审时，对外查店铺接口把营业状态报成 SUSPENDED，
 * 于是「不能上架」「不能下单」在接口层自动成立（#144）。
 * 保证金只登记金额与缴纳状态（#144 组长口径），不参与发布拦截。
 *
 * <p>审核结果与流水必须同时落库，因此提交申请与审核都在事务里写 shop 与 shop_apply_log；
 * 商品可见性同步失败则整体回滚，避免「审核已通过但商品仍在售」这类半状态。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ShopServiceImpl implements ShopService {

    private static final Set<String> SUBJECT_TYPES = Set.of("PERSONAL", "INDIVIDUAL", "ENTERPRISE");

    /** 每个店铺累计可提交入驻申请的次数上限（含首次），防刷（#144 组长要求） */
    private static final int MAX_APPLY_SUBMISSIONS = 5;

    private final ShopMapper shopMapper;
    private final ShopApplyLogMapper shopApplyLogMapper;
    private final UserMapper userMapper;
    private final ProductClient productClient;
    private final ObjectMapper objectMapper;

    @Value("${user.internal-token:}")
    private String internalToken;

    @Override
    public ShopResponse getMine(String sellerId) {
        Shop shop = shopMapper.selectBySellerId(requireSellerId(sellerId));
        if (shop == null) {
            // 还没提交过申请：不建店，返回「未申请」视图，前端据此引导卖家先申请
            ShopResponse pending = new ShopResponse();
            pending.setStatus(ShopStatus.SUSPENDED);
            pending.setPublishable(Boolean.FALSE);
            pending.setDepositPaid(Boolean.FALSE);
            return pending;
        }
        return toResponse(shop, true);
    }

    @Override
    public ShopResponse updateMine(String sellerId, ShopRequest request) {
        Shop shop = shopMapper.selectBySellerId(requireSellerId(sellerId));
        if (shop == null) {
            throw new BizException(ErrorCode.SHOP_NOT_FOUND, "请先提交入驻申请");
        }
        if (shop.getApplyStatus() != ShopApplyStatus.APPROVED) {
            throw new BizException(ErrorCode.PARAM_INVALID, "店铺审核通过后才能维护资料，店名与联系方式请通过入驻申请修改");
        }
        shop.setName(request.getName().trim());
        shop.setLogo(trimToNull(request.getLogo()));
        shop.setDescription(trimToNull(request.getDescription()));
        shop.setContactPhone(trimToNull(request.getContactPhone()));
        shop.setContactWechat(trimToNull(request.getContactWechat()));
        shop.setUpdatedAt(LocalDateTime.now());
        shopMapper.updateInfo(shop);
        log.info("卖家更新店铺资料, sellerId={}, shopId={}", sellerId, shop.getShopId());
        return toResponse(shopMapper.selectById(shop.getShopId()), true);
    }

    @Override
    @Transactional
    public ShopResponse apply(String sellerId, ShopApplyRequest request) {
        String cleanSellerId = requireSellerId(sellerId);
        String subjectType = normalizeSubjectType(request.getSubjectType());
        String qualificationJson = writeQualification(new Qualification(
                subjectType, trimToNull(request.getLicenseNo()), cleanMaterials(request.getMaterials())));

        Shop shop = shopMapper.selectBySellerId(cleanSellerId);
        if (shop != null && shop.getApplyStatus() == ShopApplyStatus.PENDING) {
            throw new BizException(ErrorCode.PARAM_INVALID, "入驻申请正在审核中，请耐心等待审核结果");
        }
        if (shop != null && shop.getApplyStatus() == ShopApplyStatus.APPROVED) {
            throw new BizException(ErrorCode.PARAM_INVALID, "店铺已通过入驻审核，无需重复申请");
        }
        if (shop != null
                && shopApplyLogMapper.countApplySubmissions(shop.getShopId()) >= MAX_APPLY_SUBMISSIONS) {
            throw new BizException(ErrorCode.PARAM_INVALID,
                    "入驻申请次数已达上限（" + MAX_APPLY_SUBMISSIONS + " 次），请联系平台处理");
        }

        String fromStatus = shop == null || shop.getApplyStatus() == null
                ? null : shop.getApplyStatus().name();
        LocalDateTime now = LocalDateTime.now();

        if (shop == null) {
            User seller = userMapper.selectById(cleanSellerId);
            if (seller == null) {
                throw new BizException(ErrorCode.USER_NOT_FOUND, "用户不存在");
            }
            Shop created = new Shop();
            created.setShopId(IdGenerator.shopId());
            created.setSellerId(cleanSellerId);
            created.setName(request.getName().trim());
            created.setContactPhone(trimToNull(request.getContactPhone()));
            created.setContactWechat(trimToNull(request.getContactWechat()));
            created.setStatus(ShopStatus.SUSPENDED);
            created.setApplyStatus(ShopApplyStatus.PENDING);
            // 新流程的店铺保证金从 0 开始，管理员审核时登记缴纳金额
            created.setDepositAmount(0L);
            created.setQualificationJson(qualificationJson);
            created.setCreatedAt(now);
            created.setUpdatedAt(now);
            created.setIsDeleted(Boolean.FALSE);
            try {
                shopMapper.insert(created);
                writeLog(created.getShopId(), fromStatus, ShopApplyStatus.PENDING, cleanSellerId, "卖家提交入驻申请");
                log.info("卖家提交入驻申请, sellerId={}, shopId={}, subjectType={}",
                        cleanSellerId, created.getShopId(), subjectType);
                return toResponse(created, true);
            } catch (DuplicateKeyException e) {
                // 并发首次申请时另一请求已建店，改为覆盖它的申请内容
                shop = shopMapper.selectBySellerId(cleanSellerId);
                if (shop == null) {
                    throw new BizException(ErrorCode.SHOP_NOT_FOUND, "店铺不存在");
                }
            }
        }

        shop.setName(request.getName().trim());
        shop.setContactPhone(trimToNull(request.getContactPhone()));
        shop.setContactWechat(trimToNull(request.getContactWechat()));
        shop.setQualificationJson(qualificationJson);
        shop.setApplyStatus(ShopApplyStatus.PENDING);
        shop.setStatus(ShopStatus.SUSPENDED);
        shop.setUpdatedAt(now);
        shopMapper.updateApply(shop);
        writeLog(shop.getShopId(), fromStatus, ShopApplyStatus.PENDING, cleanSellerId, "卖家重新提交入驻申请");
        log.info("卖家提交入驻申请, sellerId={}, shopId={}, fromStatus={}",
                cleanSellerId, shop.getShopId(), fromStatus);
        return toResponse(shopMapper.selectById(shop.getShopId()), true);
    }

    @Override
    @Transactional
    public ShopResponse audit(String operatorId, String shopId, ShopAuditRequest request) {
        ShopApplyStatus target = parseDecision(request.getDecision());
        String reason = trimToNull(request.getReason());
        if (target == ShopApplyStatus.REJECTED && reason == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "驳回时必须填写理由");
        }

        // Serialize audit decisions until the visibility change and audit log commit.
        Shop shop = shopMapper.lockById(requireShopId(shopId));
        if (shop == null) {
            throw new BizException(ErrorCode.SHOP_NOT_FOUND, "店铺不存在");
        }
        if (target == ShopApplyStatus.REJECTED && shop.getApplyStatus() == ShopApplyStatus.APPROVED) {
            throw new BizException(ErrorCode.PARAM_INVALID, "店铺已通过审核，如需停止营业请使用停用店铺");
        }

        Long deposit = target == ShopApplyStatus.APPROVED
                ? resolveDeposit(shop.getDepositAmount(), request.getDepositAmount())
                : shop.getDepositAmount();
        ShopApplyStatus fromStatus = shop.getApplyStatus();
        // 确认保证金不改变既有营业状态；首次过审才将待审店铺设为营业。
        ShopStatus targetStatus = target != ShopApplyStatus.APPROVED ? ShopStatus.SUSPENDED
                : (fromStatus == ShopApplyStatus.APPROVED ? shop.getStatus() : ShopStatus.ACTIVE);
        boolean restore = target == ShopApplyStatus.APPROVED && targetStatus == ShopStatus.ACTIVE
                && depositPaid(deposit) && !publishable(shop);
        boolean suspend = targetStatus == ShopStatus.SUSPENDED && shop.getStatus() != ShopStatus.SUSPENDED;
        shopMapper.updateAudit(shop.getShopId(), target.name(), targetStatus.name(), deposit);
        if (restore || suspend) {
            try {
                syncVisibility(shop.getSellerId(), restore);
            } catch (RuntimeException e) {
                log.error("同步商品可见性失败，审核结果已回滚, shopId={}, sellerId={}",
                        shop.getShopId(), shop.getSellerId(), e);
                throw new BizException(ErrorCode.SERVICE_UNAVAILABLE, "商品服务暂不可用，审核结果未生效，请稍后重试");
            }
        }

        writeLog(shop.getShopId(), fromStatus == null ? null : fromStatus.name(), target,
                operatorId, auditRemark(target, reason, deposit));
        log.info("管理员审核入驻申请, operatorId={}, shopId={}, fromStatus={}, toStatus={}, deposit={}",
                operatorId, shop.getShopId(), fromStatus, target, deposit);
        return toResponse(shopMapper.selectById(shop.getShopId()), true);
    }

    @Override
    public PageResponse<ShopResponse> list(String keyword, String status, String applyStatus, int page, int size) {
        String safeStatus = normalizeStatus(status);
        String safeApplyStatus = normalizeApplyStatus(applyStatus);
        int safePage = Math.max(1, page);
        int safeSize = Math.min(100, Math.max(1, size));
        String safeKeyword = trimToNull(keyword);
        long total = shopMapper.countPage(safeKeyword, safeStatus, safeApplyStatus);
        List<ShopResponse> list = shopMapper.selectPage(safeKeyword, safeStatus, safeApplyStatus,
                        (safePage - 1) * safeSize, safeSize)
                .stream()
                .map(this::toListResponse)
                .toList();
        return PageResponse.of(safePage, safeSize, total, list);
    }

    @Override
    public ShopResponse updateStatus(String operatorId, String shopId, ShopStatusRequest request) {
        ShopStatus target = parseStatus(request.getStatus());
        Shop shop = shopMapper.selectById(shopId);
        if (shop == null) {
            throw new BizException(ErrorCode.SHOP_NOT_FOUND, "店铺不存在");
        }
        if (target == ShopStatus.ACTIVE && shop.getApplyStatus() != ShopApplyStatus.APPROVED) {
            throw new BizException(ErrorCode.PARAM_INVALID, "入驻审核未通过，不能恢复营业");
        }
        if (shop.getStatus() == target) {
            // 状态没变化也补同步一次：之前同步失败或人工改过库时能自愈，同步本身是幂等的，
            // 这一步失败只记日志，不改变本次返回结果
            try {
                if (target == ShopStatus.SUSPENDED || depositPaid(shop.getDepositAmount())) {
                    syncVisibility(shop.getSellerId(), target == ShopStatus.ACTIVE);
                }
            } catch (RuntimeException e) {
                log.warn("店铺状态未变化，补同步商品可见性失败, shopId={}, sellerId={}",
                        shopId, shop.getSellerId(), e);
            }
            return toResponse(shop, true);
        }

        shopMapper.updateStatus(shopId, target.name());
        try {
            if (target == ShopStatus.SUSPENDED || depositPaid(shop.getDepositAmount())) {
                syncVisibility(shop.getSellerId(), target == ShopStatus.ACTIVE);
            }
        } catch (RuntimeException e) {
            // 商品可见性没同步成功就退回原状态，保证"店铺状态"与"商品是否可见"不打架
            shopMapper.updateStatus(shopId, shop.getStatus().name());
            log.error("同步商品可见性失败，已回滚店铺状态, shopId={}, sellerId={}", shopId, shop.getSellerId(), e);
            throw new BizException(ErrorCode.SERVICE_UNAVAILABLE, "商品服务暂不可用，营业状态未生效，请稍后重试");
        }
        log.info("管理员变更店铺营业状态, operatorId={}, shopId={}, sellerId={}, status={}",
                operatorId, shopId, shop.getSellerId(), target);
        return toResponse(shopMapper.selectById(shopId), true);
    }

    @Override
    public ShopResponse getById(String shopId) {
        if (shopId == null || shopId.isBlank()) {
            throw new BizException(ErrorCode.PARAM_INVALID, "店铺ID不能为空");
        }
        Shop shop = shopMapper.selectById(shopId.trim());
        if (shop == null || Boolean.TRUE.equals(shop.getIsDeleted())) {
            throw new BizException(ErrorCode.SHOP_NOT_FOUND, "店铺不存在");
        }
        return toResponse(shop);
    }

    @Override
    public ShopResponse getBySellerId(String sellerId) {
        if (sellerId == null || sellerId.isBlank()) {
            throw new BizException(ErrorCode.PARAM_INVALID, "卖家ID不能为空");
        }
        Shop shop = shopMapper.selectBySellerId(sellerId.trim());
        if (shop == null) {
            throw new BizException(ErrorCode.SHOP_NOT_FOUND, "店铺不存在");
        }
        ShopResponse response = toResponse(shop);
        if (shop.getApplyStatus() != ShopApplyStatus.APPROVED) {
            // 未过审对外一律按「不营业」，product-service 与 order-service 沿用 #49 的拦截
            response.setStatus(ShopStatus.SUSPENDED);
        }
        return response;
    }

    @Override
    public PublicationEligibility publicationEligibility(String sellerId) {
        Shop shop = shopMapper.selectBySellerId(requireSellerId(sellerId));
        if (shop == null || shop.getApplyStatus() != ShopApplyStatus.APPROVED) {
            return new PublicationEligibility(false, "请先完成店铺入驻审核");
        }
        if (shop.getStatus() != ShopStatus.ACTIVE) {
            return new PublicationEligibility(false, "店铺已停用，暂不能发布或上架商品");
        }
        if (!depositPaid(shop.getDepositAmount())) {
            return new PublicationEligibility(false, "保证金尚未登记，请联系管理员");
        }
        return new PublicationEligibility(true, null);
    }

    private void syncVisibility(String sellerId, boolean visible) {
        Result<?> result = productClient.syncSellerVisibility(internalToken,
                new SellerVisibilityRequest(sellerId, visible));
        if (result == null || result.getCode() != ErrorCode.SUCCESS) {
            throw new BizException(ErrorCode.SERVICE_UNAVAILABLE,
                    result == null ? "product-service 无响应" : result.getMessage());
        }
    }

    private void writeLog(String shopId, String fromStatus, ShopApplyStatus toStatus,
                          String operatorId, String remark) {
        ShopApplyLog entry = new ShopApplyLog();
        entry.setShopId(shopId);
        entry.setFromStatus(fromStatus);
        entry.setToStatus(toStatus.name());
        entry.setOperatorId(operatorId);
        entry.setRemark(remark);
        entry.setCreatedAt(LocalDateTime.now());
        shopApplyLogMapper.insert(entry);
    }

    /** 保证金已缴纳 = 管理员登记金额大于 0；不发起任何在线扣款。 */
    private static boolean depositPaid(Long depositAmount) {
        return depositAmount != null && depositAmount > 0;
    }

    private static boolean publishable(Shop shop) {
        return shop.getApplyStatus() == ShopApplyStatus.APPROVED
                && shop.getStatus() == ShopStatus.ACTIVE
                && depositPaid(shop.getDepositAmount());
    }

    private static Long resolveDeposit(Long current, Long submitted) {
        if (submitted != null) {
            return submitted;
        }
        return current == null ? 0L : current;
    }

    private static String auditRemark(ShopApplyStatus decision, String reason, Long deposit) {
        if (decision == ShopApplyStatus.REJECTED) {
            return reason;
        }
        if (deposit == null) {
            return "审核通过";
        }
        return deposit > 0
                ? "审核通过，已登记保证金 " + deposit + " 分"
                : "审核通过，保证金未缴纳";
    }

    private String requireSellerId(String sellerId) {
        if (sellerId == null || sellerId.isBlank()) {
            throw new BizException(ErrorCode.UNAUTHORIZED, "未登录或用户信息缺失");
        }
        return sellerId.trim();
    }

    private static String requireShopId(String shopId) {
        if (shopId == null || shopId.isBlank()) {
            throw new BizException(ErrorCode.PARAM_INVALID, "店铺ID不能为空");
        }
        return shopId.trim();
    }

    private static String normalizeSubjectType(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new BizException(ErrorCode.PARAM_INVALID, "主体类型不能为空");
        }
        String upper = raw.trim().toUpperCase(Locale.ROOT);
        if (!SUBJECT_TYPES.contains(upper)) {
            throw new BizException(ErrorCode.PARAM_INVALID, "主体类型不合法，只允许 PERSONAL / INDIVIDUAL / ENTERPRISE");
        }
        return upper;
    }

    private static List<String> cleanMaterials(List<String> materials) {
        if (materials == null) {
            return List.of();
        }
        return materials.stream()
                .filter(item -> item != null && !item.isBlank())
                .map(String::trim)
                .limit(9)
                .toList();
    }

    private static ShopApplyStatus parseDecision(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new BizException(ErrorCode.PARAM_INVALID, "审核结论不能为空");
        }
        String upper = raw.trim().toUpperCase(Locale.ROOT);
        if (!ShopApplyStatus.APPROVED.name().equals(upper) && !ShopApplyStatus.REJECTED.name().equals(upper)) {
            throw new BizException(ErrorCode.PARAM_INVALID, "审核结论不合法，只允许 APPROVED 或 REJECTED");
        }
        return ShopApplyStatus.valueOf(upper);
    }

    private static String normalizeStatus(String status) {
        if (status == null || status.isBlank()) {
            return null;
        }
        try {
            return ShopStatus.valueOf(status.trim().toUpperCase(Locale.ROOT)).name();
        } catch (IllegalArgumentException e) {
            throw new BizException(ErrorCode.PARAM_INVALID, "营业状态不合法");
        }
    }

    private static String normalizeApplyStatus(String applyStatus) {
        if (applyStatus == null || applyStatus.isBlank()) {
            return null;
        }
        String upper = applyStatus.trim().toUpperCase(Locale.ROOT);
        if ("NONE".equals(upper)) {
            // 未申请的卖家没有 shop 行，列表里不存在这类数据
            throw new BizException(ErrorCode.PARAM_INVALID, "审核状态不合法，只允许 PENDING / APPROVED / REJECTED");
        }
        try {
            return ShopApplyStatus.valueOf(upper).name();
        } catch (IllegalArgumentException e) {
            throw new BizException(ErrorCode.PARAM_INVALID, "审核状态不合法，只允许 PENDING / APPROVED / REJECTED");
        }
    }

    private static ShopStatus parseStatus(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new BizException(ErrorCode.PARAM_INVALID, "营业状态不能为空");
        }
        try {
            return ShopStatus.valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new BizException(ErrorCode.PARAM_INVALID, "营业状态不合法，只允许 ACTIVE 或 SUSPENDED");
        }
    }

    /** 列表行只在已驳回时补查一次理由，避免整页 N+1 */
    private ShopResponse toListResponse(Shop shop) {
        return toResponse(shop, shop.getApplyStatus() == ShopApplyStatus.REJECTED);
    }

    /**
     * 公开视图：#142 的 GET /shops/{shopId} 被网关列为匿名可读，因此这里按白名单只给
     * #144 之前就公开的字段。资质证照、保证金与审核状态属于卖家隐私，新增字段默认不外泄。
     */
    private ShopResponse toResponse(Shop shop) {
        ShopResponse response = new ShopResponse();
        response.setShopId(shop.getShopId());
        response.setSellerId(shop.getSellerId());
        response.setName(shop.getName());
        response.setLogo(shop.getLogo());
        response.setDescription(shop.getDescription());
        response.setContactPhone(shop.getContactPhone());
        response.setContactWechat(shop.getContactWechat());
        response.setStatus(shop.getStatus());
        response.setFreightTemplateId(shop.getFreightTemplateId());
        response.setCreatedAt(shop.getCreatedAt());
        response.setUpdatedAt(shop.getUpdatedAt());
        return response;
    }

    private ShopResponse toResponse(Shop shop, boolean withRejectReason) {
        ShopResponse response = new ShopResponse();
        response.setShopId(shop.getShopId());
        response.setSellerId(shop.getSellerId());
        response.setName(shop.getName());
        response.setLogo(shop.getLogo());
        response.setDescription(shop.getDescription());
        response.setContactPhone(shop.getContactPhone());
        response.setContactWechat(shop.getContactWechat());
        response.setStatus(shop.getStatus());
        response.setCreatedAt(shop.getCreatedAt());
        response.setUpdatedAt(shop.getUpdatedAt());
        response.setApplyStatus(shop.getApplyStatus());
        response.setDepositAmount(shop.getDepositAmount());
        response.setDepositPaid(depositPaid(shop.getDepositAmount()));
        response.setPublishable(publishable(shop));
        readQualification(shop.getQualificationJson(), response);
        if (withRejectReason) {
            ShopApplyLog rejected = shopApplyLogMapper.selectLatestReject(shop.getShopId());
            response.setRejectReason(rejected == null ? null : rejected.getRemark());
        }
        return response;
    }

    private String writeQualification(Qualification qualification) {
        try {
            return objectMapper.writeValueAsString(qualification);
        } catch (Exception e) {
            log.error("店铺资质信息序列化失败", e);
            throw new BizException(ErrorCode.PARAM_INVALID, "资质信息格式不正确");
        }
    }

    private void readQualification(String json, ShopResponse response) {
        if (json == null || json.isBlank()) {
            return;
        }
        try {
            Qualification qualification = objectMapper.readValue(json, Qualification.class);
            response.setSubjectType(qualification.subjectType());
            response.setLicenseNo(qualification.licenseNo());
            response.setMaterials(qualification.materials());
        } catch (Exception e) {
            // 资质 JSON 是管理员只读的附加信息，脏数据不该让整条店铺查询失败
            log.warn("店铺资质信息解析失败, shopId={}", response.getShopId(), e);
        }
    }

    private static String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    /** 入驻资质快照，序列化后存进 shop.qualification_json */
    private record Qualification(String subjectType, String licenseNo, List<String> materials) {
    }
}
