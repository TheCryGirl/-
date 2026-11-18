package com.mall.user.controller;

import com.mall.user.common.ErrorCode;
import com.mall.user.dto.request.ShopApplyRequest;
import com.mall.user.dto.response.PageResponse;
import com.mall.user.dto.response.ShopResponse;
import com.mall.user.exception.GlobalExceptionHandler;
import com.mall.user.service.ShopFollowService;
import com.mall.user.service.ShopService;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 入驻申请与审核接口的权限边界（#144）：审核只给管理员，申请只给卖家。
 */
class ShopAuditPermissionTest {

    private MockMvc mvc(ShopService shopService) {
        return MockMvcBuilders.standaloneSetup(new ShopController(shopService, mock(ShopFollowService.class)))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void sellerCannotAuditItsOwnApplication() throws Exception {
        ShopService shopService = mock(ShopService.class);

        mvc(shopService).perform(put("/shops/S1/audit")
                        .header("X-User-Id", "seller-1").header("X-User-Role", "SELLER")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"decision\":\"APPROVED\",\"depositAmount\":50000}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(ErrorCode.FORBIDDEN));

        verifyNoInteractions(shopService);
    }

    @Test
    void buyerCannotApplyForShopOrFilterAuditList() throws Exception {
        ShopService shopService = mock(ShopService.class);

        mvc(shopService).perform(post("/shops/apply")
                        .header("X-User-Id", "buyer-1").header("X-User-Role", "BUYER")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"小店\",\"subjectType\":\"PERSONAL\"}"))
                .andExpect(jsonPath("$.code").value(ErrorCode.FORBIDDEN));
        mvc(shopService).perform(get("/shops").header("X-User-Role", "BUYER")
                        .param("applyStatus", "PENDING"))
                .andExpect(jsonPath("$.code").value(ErrorCode.FORBIDDEN));

        verify(shopService, never()).apply(any(), any());
        verify(shopService, never()).list(any(), any(), any(), anyInt(), anyInt());
    }

    @Test
    void adminAuditAndPendingFilterReachService() throws Exception {
        ShopService shopService = mock(ShopService.class);
        when(shopService.audit(eq("admin-1"), eq("S1"), any())).thenReturn(new ShopResponse());
        when(shopService.list(isNull(), isNull(), eq("PENDING"), eq(1), eq(20)))
                .thenReturn(PageResponse.of(1, 20, 0, List.of()));

        mvc(shopService).perform(put("/shops/S1/audit")
                        .header("X-User-Id", "admin-1").header("X-User-Role", "ADMIN")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"decision\":\"REJECTED\",\"reason\":\"资质材料不清晰\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(ErrorCode.SUCCESS));
        mvc(shopService).perform(get("/shops").header("X-User-Role", "ADMIN")
                        .param("applyStatus", "PENDING"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(ErrorCode.SUCCESS));

        verify(shopService).audit(eq("admin-1"), eq("S1"), any());
        verify(shopService).list(null, null, "PENDING", 1, 20);
    }

    @Test
    void sellerApplyPassesItsIdentityToService() throws Exception {
        ShopService shopService = mock(ShopService.class);
        when(shopService.apply(eq("seller-9"), any(ShopApplyRequest.class))).thenReturn(new ShopResponse());

        mvc(shopService).perform(post("/shops/apply")
                        .header("X-User-Id", "seller-9").header("X-User-Role", "SELLER")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"新申请店\",\"subjectType\":\"enterprise\",\"materials\":[\"/images/a.png\"]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(ErrorCode.SUCCESS));

        verify(shopService).apply(eq("seller-9"), any(ShopApplyRequest.class));
    }
}
