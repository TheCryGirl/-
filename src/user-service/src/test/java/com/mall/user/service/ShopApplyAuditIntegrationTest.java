package com.mall.user.service;

import com.mall.user.client.ProductClient;
import com.mall.user.client.dto.SellerVisibilityRequest;
import com.mall.user.common.BizException;
import com.mall.user.common.ErrorCode;
import com.mall.user.common.IdGenerator;
import com.mall.user.common.Result;
import com.mall.user.common.ShopApplyStatus;
import com.mall.user.common.ShopStatus;
import com.mall.user.dto.request.RegisterRequest;
import com.mall.user.dto.request.ShopApplyRequest;
import com.mall.user.dto.request.ShopAuditRequest;
import com.mall.user.dto.request.ShopRequest;
import com.mall.user.dto.request.ShopStatusRequest;
import com.mall.user.dto.response.PageResponse;
import com.mall.user.dto.response.ShopResponse;
import com.mall.user.entity.Shop;
import com.mall.user.entity.ShopApplyLog;
import com.mall.user.mapper.ShopApplyLogMapper;
import com.mall.user.mapper.ShopMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
/**
 * 商家入驻与资质审核的集成测试（#144 第一块）。
 *
 * <p>product-client 用 mock 顶替：测试里不启 product-service，但「审核通过要同步商品可见性」
 * 这条链路必须能验证到。
 */
@SpringBootTest
@ActiveProfiles("test")
class ShopApplyAuditIntegrationTest {

    @Autowired
    private ShopService shopService;

    @Autowired
    private UserService userService;

    @Autowired
    private ShopMapper shopMapper;

    @Autowired
    private ShopApplyLogMapper shopApplyLogMapper;

    @MockBean
    private ProductClient productClient;

    @BeforeEach
    void productServiceAlwaysSucceeds() {
        when(productClient.syncSellerVisibility(any(), any())).thenReturn(Result.success(null));
    }

    @Test
    @Transactional
    void applyCreatesPendingShopThatCannotPublish() {
        String sellerId = newSeller();
        ShopApplyRequest request = applyRequest("入驻待审店", "ENTERPRISE");
        request.setLicenseNo("91110000MA01ABCD2X");
        request.setMaterials(Arrays.asList("/images/shop1.png", "  ", null, "/images/shop2.png"));

        ShopResponse applied = shopService.apply(sellerId, request);

        assertEquals(ShopApplyStatus.PENDING, applied.getApplyStatus());
        assertFalse(applied.getPublishable());
        assertFalse(applied.getDepositPaid());
        assertEquals(0L, applied.getDepositAmount().longValue());
        assertEquals("ENTERPRISE", applied.getSubjectType());
        assertEquals("91110000MA01ABCD2X", applied.getLicenseNo());
        assertEquals(List.of("/images/shop1.png", "/images/shop2.png"), applied.getMaterials());
        Shop stored = shopMapper.selectById(applied.getShopId());
        assertEquals(ShopStatus.SUSPENDED, stored.getStatus());

        List<ShopApplyLog> logs = shopApplyLogMapper.selectByShopId(applied.getShopId());
        assertEquals(1, logs.size());
        assertNull(logs.get(0).getFromStatus());
        assertEquals("PENDING", logs.get(0).getToStatus());
        assertEquals(sellerId, logs.get(0).getOperatorId());
    }

    @Test
    @Transactional
    void pendingApplicationCannotBeResubmittedOrEdited() {
        String sellerId = newSeller();
        shopService.apply(sellerId, applyRequest("待审核店", "PERSONAL"));

        BizException again = assertThrows(BizException.class,
                () -> shopService.apply(sellerId, applyRequest("待审核店", "PERSONAL")));
        assertEquals(ErrorCode.PARAM_INVALID, again.getCode());

        ShopRequest edit = new ShopRequest();
        edit.setName("审核中改名");
        BizException editError = assertThrows(BizException.class,
                () -> shopService.updateMine(sellerId, edit));
        assertEquals(ErrorCode.PARAM_INVALID, editError.getCode());
    }

    @Test
    void mineWithoutShopIsNotCreatedAutomatically() {
        String sellerId = newSeller();

        ShopResponse mine = shopService.getMine(sellerId);

        assertNull(mine.getShopId());
        assertEquals(ShopStatus.SUSPENDED, mine.getStatus());
        assertFalse(mine.getPublishable());
        assertNull(shopMapper.selectBySellerId(sellerId));

        BizException saveError = assertThrows(BizException.class, () -> {
            ShopRequest request = new ShopRequest();
            request.setName("还没申请的店");
            shopService.updateMine(sellerId, request);
        });
        assertEquals(ErrorCode.SHOP_NOT_FOUND, saveError.getCode());
    }

    @Test
    @Transactional
    void approvedAuditActivatesShopAndSyncsProducts() {
        String sellerId = newSeller();
        String adminId = newSeller();
        String shopId = shopService.apply(sellerId, applyRequest("审核通过店", "INDIVIDUAL")).getShopId();

        ShopResponse audited = shopService.audit(adminId, shopId, auditRequest("APPROVED", null, 50000L));

        assertEquals(ShopApplyStatus.APPROVED, audited.getApplyStatus());
        assertEquals(ShopStatus.ACTIVE, shopMapper.selectById(shopId).getStatus());
        assertTrue(audited.getPublishable());
        assertEquals(50000L, audited.getDepositAmount().longValue());
        verify(productClient).syncSellerVisibility(any(), eq(new SellerVisibilityRequest(sellerId, true)));

        List<ShopApplyLog> logs = shopApplyLogMapper.selectByShopId(shopId);
        assertEquals(2, logs.size());
        assertEquals("PENDING", logs.get(1).getFromStatus());
        assertEquals("APPROVED", logs.get(1).getToStatus());
        assertEquals(adminId, logs.get(1).getOperatorId());
        assertTrue(logs.get(1).getRemark().contains("50000"));
    }

    @Test
    @Transactional
    void approvedAuditWithoutDepositStillActivatesShop() {
        String sellerId = newSeller();
        String adminId = newSeller();
        String shopId = shopService.apply(sellerId, applyRequest("保证金未缴店", "PERSONAL")).getShopId();

        // 保证金只登记金额与缴纳状态（组长口径）：通过即恢复营业，不拦截发布
        ShopResponse audited = shopService.audit(adminId, shopId, auditRequest("APPROVED", null, 0L));

        assertEquals(ShopApplyStatus.APPROVED, audited.getApplyStatus());
        assertFalse(audited.getDepositPaid());
        assertTrue(audited.getPublishable());
        assertEquals(0L, audited.getDepositAmount().longValue());
        assertEquals(ShopStatus.ACTIVE, shopMapper.selectById(shopId).getStatus());
        verify(productClient).syncSellerVisibility(any(), eq(new SellerVisibilityRequest(sellerId, true)));
    }

    @Test
    @Transactional
    void pendingShopCannotBeResumedToActive() {
        String sellerId = newSeller();
        String adminId = newSeller();
        String shopId = shopService.apply(sellerId, applyRequest("待审恢复店", "PERSONAL")).getShopId();

        // 未过审的店铺即使手工改库也恢复不了 ACTIVE
        shopMapper.updateStatus(shopId, ShopStatus.ACTIVE.name());
        assertEquals(ShopStatus.SUSPENDED, shopService.getBySellerId(sellerId).getStatus());

        BizException resume = assertThrows(BizException.class,
                () -> shopService.updateStatus(adminId, shopId, statusRequest("ACTIVE")));
        assertEquals(ErrorCode.PARAM_INVALID, resume.getCode());
    }

    @Test
    @Transactional
    void applySubmissionsAreCapped() {
        String sellerId = newSeller();
        String adminId = newSeller();
        String shopId = shopService.apply(sellerId, applyRequest("次数上限店", "PERSONAL")).getShopId();

        // 首次 1 次 + 重提 4 次 = 5 次申请全部允许
        for (int i = 0; i < 4; i++) {
            shopService.audit(adminId, shopId, auditRequest("REJECTED", "第" + (i + 1) + "次驳回", null));
            shopService.apply(sellerId, applyRequest("次数上限店", "PERSONAL"));
        }
        shopService.audit(adminId, shopId, auditRequest("REJECTED", "第5次驳回", null));

        // 第 6 次提交被防刷上限拦住
        BizException capped = assertThrows(BizException.class,
                () -> shopService.apply(sellerId, applyRequest("次数上限店", "PERSONAL")));
        assertEquals(ErrorCode.PARAM_INVALID, capped.getCode());
        assertEquals(ShopApplyStatus.REJECTED, shopMapper.selectById(shopId).getApplyStatus());
    }

    @Test
    @Transactional
    void rejectedAuditNeedsReasonAndKeepsTraceOnResubmit() {
        String sellerId = newSeller();
        String adminId = newSeller();
        String shopId = shopService.apply(sellerId, applyRequest("资质不全店", "ENTERPRISE")).getShopId();

        BizException noReason = assertThrows(BizException.class,
                () -> shopService.audit(adminId, shopId, auditRequest("REJECTED", " ", null)));
        assertEquals(ErrorCode.PARAM_INVALID, noReason.getCode());

        ShopResponse rejected = shopService.audit(adminId, shopId, auditRequest("REJECTED", "资质材料不清晰", null));
        assertEquals(ShopApplyStatus.REJECTED, rejected.getApplyStatus());
        assertEquals(ShopStatus.SUSPENDED, shopMapper.selectById(shopId).getStatus());
        assertEquals("资质材料不清晰", shopService.getMine(sellerId).getRejectReason());

        ShopResponse resubmitted = shopService.apply(sellerId, applyRequest("资质补全店", "ENTERPRISE"));
        assertEquals(ShopApplyStatus.PENDING, resubmitted.getApplyStatus());
        assertEquals("资质材料不清晰", resubmitted.getRejectReason());

        List<ShopApplyLog> logs = shopApplyLogMapper.selectByShopId(shopId);
        assertEquals(3, logs.size());
        assertEquals("PENDING", logs.get(1).getFromStatus());
        assertEquals("REJECTED", logs.get(1).getToStatus());
        assertEquals("资质材料不清晰", logs.get(1).getRemark());
        assertEquals("REJECTED", logs.get(2).getFromStatus());
        assertEquals("PENDING", logs.get(2).getToStatus());
    }

    @Test
    void concurrentRejectionCannotOverwriteApproval() throws Exception {
        String sellerId = newSeller();
        String shopId = shopService.apply(sellerId, applyRequest("并发审核店", "PERSONAL")).getShopId();
        var approving = new java.util.concurrent.CountDownLatch(1);
        var allowCommit = new java.util.concurrent.CountDownLatch(1);
        var rejecting = new java.util.concurrent.CountDownLatch(1);
        var workers = java.util.concurrent.Executors.newFixedThreadPool(2);
        when(productClient.syncSellerVisibility(any(), eq(new SellerVisibilityRequest(sellerId, true))))
                .thenAnswer(call -> {
                    approving.countDown();
                    assertTrue(allowCommit.await(10, java.util.concurrent.TimeUnit.SECONDS));
                    return Result.success(null);
                });
        try {
            var approval = workers.submit(() -> shopService.audit("ADMIN-A", shopId,
                    auditRequest("APPROVED", null, 10000L)));
            assertTrue(approving.await(10, java.util.concurrent.TimeUnit.SECONDS));
            var rejection = workers.submit(() -> {
                rejecting.countDown();
                return shopService.audit("ADMIN-B", shopId, auditRequest("REJECTED", "重复决定", null));
            });
            assertTrue(rejecting.await(10, java.util.concurrent.TimeUnit.SECONDS));
            try {
                assertThrows(java.util.concurrent.TimeoutException.class,
                        () -> rejection.get(500, java.util.concurrent.TimeUnit.MILLISECONDS));
            } finally {
                allowCommit.countDown();
            }
            approval.get(10, java.util.concurrent.TimeUnit.SECONDS);
            var error = assertThrows(java.util.concurrent.ExecutionException.class,
                    () -> rejection.get(10, java.util.concurrent.TimeUnit.SECONDS));
            assertTrue(error.getCause() instanceof BizException);
            assertEquals(ErrorCode.PARAM_INVALID, ((BizException) error.getCause()).getCode());
            assertEquals(ShopApplyStatus.APPROVED, shopMapper.selectById(shopId).getApplyStatus());
            assertEquals(2, shopApplyLogMapper.selectByShopId(shopId).size());
        } finally {
            allowCommit.countDown();
            workers.shutdownNow();
        }
    }

    @Test
    @Transactional
    void approvedShopCannotBeRejectedRetroactively() {
        String sellerId = newSeller();
        String adminId = newSeller();
        String shopId = shopService.apply(sellerId, applyRequest("已通过店", "PERSONAL")).getShopId();
        shopService.audit(adminId, shopId, auditRequest("APPROVED", null, 10000L));

        BizException reject = assertThrows(BizException.class,
                () -> shopService.audit(adminId, shopId, auditRequest("REJECTED", "事后想驳回", null)));
        assertEquals(ErrorCode.PARAM_INVALID, reject.getCode());
        assertEquals(ShopApplyStatus.APPROVED, shopMapper.selectById(shopId).getApplyStatus());
    }

    @Test
    void legacyShopWithoutApplyRecordStaysPublishable() {
        String sellerId = newSeller();
        Shop legacy = new Shop();
        legacy.setShopId(IdGenerator.shopId());
        legacy.setSellerId(sellerId);
        legacy.setName("规则生效前的老店");
        legacy.setStatus(ShopStatus.ACTIVE);
        legacy.setApplyStatus(ShopApplyStatus.APPROVED);
        legacy.setDepositAmount(0L);
        legacy.setCreatedAt(LocalDateTime.now());
        legacy.setUpdatedAt(LocalDateTime.now());
        legacy.setIsDeleted(Boolean.FALSE);
        shopMapper.insert(legacy);

        // 存量店铺默认 APPROVED：不受影响；保证金 0 只表示「未缴纳」，仅作记录
        ShopResponse mine = shopService.getMine(sellerId);
        assertEquals(0L, mine.getDepositAmount());
        assertFalse(mine.getDepositPaid());
        assertTrue(mine.getPublishable());
        assertEquals(ShopStatus.ACTIVE, shopService.getBySellerId(sellerId).getStatus());

        ShopRequest request = new ShopRequest();
        request.setName("老店改名");
        assertEquals("老店改名", shopService.updateMine(sellerId, request).getName());
    }

    @Test
    @Transactional
    void listFiltersByApplyStatusAndValidatesIt() {
        String sellerId = newSeller();
        String shopId = shopService.apply(sellerId, applyRequest("列表筛选店", "PERSONAL")).getShopId();

        PageResponse<ShopResponse> pending = shopService.list(null, null, "pending", 1, 100);
        assertTrue(pending.getList().stream().anyMatch(shop -> shopId.equals(shop.getShopId())));
        assertTrue(pending.getList().stream().allMatch(shop -> shop.getApplyStatus() == ShopApplyStatus.PENDING));

        BizException illegal = assertThrows(BizException.class,
                () -> shopService.list(null, null, "NONE", 1, 10));
        assertEquals(ErrorCode.PARAM_INVALID, illegal.getCode());
    }

    @Test
    @Transactional
    void publicShopHomeHidesApplyAndQualificationFields() {
        String sellerId = newSeller();
        String adminId = newSeller();
        ShopApplyRequest request = applyRequest("公开主页店", "ENTERPRISE");
        request.setLicenseNo("91110000MA01ABCD2X");
        request.setMaterials(List.of("/images/license.png"));
        String shopId = shopService.apply(sellerId, request).getShopId();
        shopService.audit(adminId, shopId, auditRequest("REJECTED", "证照模糊", null));

        // #142 的店铺主页匿名可读，#144 新增的资质与保证金字段不能跟着外泄
        ShopResponse publicView = shopService.getById(shopId);
        assertEquals(shopId, publicView.getShopId());
        assertEquals(sellerId, publicView.getSellerId());
        assertEquals("公开主页店", publicView.getName());
        assertEquals(ShopStatus.SUSPENDED, publicView.getStatus());
        assertNull(publicView.getApplyStatus());
        assertNull(publicView.getRejectReason());
        assertNull(publicView.getDepositAmount());
        assertNull(publicView.getDepositPaid());
        assertNull(publicView.getPublishable());
        assertNull(publicView.getSubjectType());
        assertNull(publicView.getLicenseNo());
        assertNull(publicView.getMaterials());

        // 卖家自己看仍然完整，说明脱敏只发生在公开视图
        ShopResponse mine = shopService.getMine(sellerId);
        assertEquals(ShopApplyStatus.REJECTED, mine.getApplyStatus());
        assertEquals("证照模糊", mine.getRejectReason());
        assertEquals("91110000MA01ABCD2X", mine.getLicenseNo());
        assertEquals(List.of("/images/license.png"), mine.getMaterials());
    }

    private String newSeller() {
        RegisterRequest request = new RegisterRequest();
        String suffix = String.valueOf(System.nanoTime() % 1_000_000_000L);
        request.setAccount("s" + suffix);
        request.setPassword("123456");
        request.setNickname("卖家" + suffix);
        return userService.register(request);
    }

    private ShopApplyRequest applyRequest(String name, String subjectType) {
        ShopApplyRequest request = new ShopApplyRequest();
        request.setName(name);
        request.setContactPhone("13800138000");
        request.setSubjectType(subjectType);
        return request;
    }

    private ShopAuditRequest auditRequest(String decision, String reason, Long deposit) {
        ShopAuditRequest request = new ShopAuditRequest();
        request.setDecision(decision);
        request.setReason(reason);
        request.setDepositAmount(deposit);
        return request;
    }

    private ShopStatusRequest statusRequest(String status) {
        ShopStatusRequest request = new ShopStatusRequest();
        request.setStatus(status);
        return request;
    }
}
