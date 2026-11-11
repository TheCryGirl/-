package com.mall.product;

import com.mall.product.common.BizException;
import com.mall.product.common.Result;
import com.mall.product.config.InternalTokenVerifier;
import com.mall.product.controller.ProductController;
import com.mall.product.service.AuditLogPublisher;
import com.mall.product.service.ProductService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Exercises the real HTTP controller boundary, including a freshly logged-in BUYER after demotion. */
class ProductWriterRoleRegressionTest {
    ProductService service;
    MockMvc mvc;
    @BeforeEach void setup() {
        service=mock(ProductService.class);
        mvc=MockMvcBuilders.standaloneSetup(new ProductController(service,mock(InternalTokenVerifier.class),mock(AuditLogPublisher.class)))
                .setControllerAdvice(new Errors()).build();
    }
    MockHttpServletRequestBuilder request(int op) {
        return switch(op) {
            case 0 -> put("/products/P1").contentType(MediaType.APPLICATION_JSON).content("{\"title\":\"edited\"}");
            case 1 -> put("/products/P1/status").contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"ON_SALE\"}");
            case 2 -> delete("/products/P1");
            case 3 -> put("/products/skus/S1/stock").contentType(MediaType.APPLICATION_JSON).content("{\"stockAvailable\":5}");
            case 4 -> multipart("/products/images").file(new MockMultipartFile("file","a.png","image/png",new byte[]{1}));
            default -> throw new IllegalArgumentException();
        };
    }
    @Test void buyerAdminMissingAndInvalidRoleCannotUseOwnedProductWriteEndpoints() throws Exception {
        for(String role:new String[]{"BUYER","ADMIN","","invalid"}) {
            for(int op=0;op<5;op++) {
                var r=request(op).header("X-User-Id","former-seller");
                if(!role.isEmpty())r.header("X-User-Role",role);
                mvc.perform(r).andExpect(status().isOk()).andExpect(jsonPath("$.code").value(10003));
            }
        }
        verifyNoInteractions(service);
    }
    @Test void currentSellerCanReachExistingBusinessOwnershipChecks() throws Exception {
        for(int op=0;op<5;op++)mvc.perform(request(op).header("X-User-Id","owner").header("X-User-Role","SELLER"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(0));
        verify(service).update(eq("P1"),eq("owner"),any());
        verify(service).updateStatus(eq("P1"),eq("owner"),any());
        verify(service).delete("P1","owner");
        verify(service).updateStock(eq("S1"),eq("owner"),any());
        verify(service).uploadImage(any());
    }
    @RestControllerAdvice static class Errors {
        @ExceptionHandler(BizException.class) Result<Void> business(BizException e){return Result.fail(e.getCode(),e.getMessage());}
    }
}
