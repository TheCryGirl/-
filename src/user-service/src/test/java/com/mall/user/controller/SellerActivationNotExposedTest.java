package com.mall.user.controller;

import com.mall.user.service.*;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class SellerActivationNotExposedTest {
    @Test
    void authenticatedBuyerCannotActivateSellerThroughRemovedEndpoint() throws Exception {
        UserService users = mock(UserService.class);
        var mvc = MockMvcBuilders.standaloneSetup(new UserController(users, mock(AddressService.class),
                mock(AvatarStorageService.class), mock(AuditLogPublisher.class))).build();
        mvc.perform(post("/users/me/seller").header("X-User-Id", "buyer")
                .header("X-User-Role", "BUYER")).andExpect(status().isNotFound());
        verifyNoInteractions(users);
    }
}
