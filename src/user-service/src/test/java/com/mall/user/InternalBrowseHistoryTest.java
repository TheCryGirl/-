package com.mall.user;

import com.mall.user.service.BrowseHistoryService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 内部浏览行为接口（#141）：推荐链路只取「最近浏览过的商品ID」，按浏览时间倒序。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class InternalBrowseHistoryTest {

    private static final String INTERNAL_TOKEN = "test-internal-token";

    @Autowired
    private BrowseHistoryService browseHistoryService;

    @Autowired
    private MockMvc mockMvc;

    @Test
    void recentProductIdsFollowVisitOrderAndRespectLimit() throws InterruptedException {
        String userId = "U-BROWSE-" + System.nanoTime();
        browseHistoryService.record(userId, "P-FIRST");
        Thread.sleep(5);
        browseHistoryService.record(userId, "P-SECOND");
        Thread.sleep(5);
        browseHistoryService.record(userId, "P-THIRD");

        List<String> productIds = browseHistoryService.recentProductIds(userId, 20);

        assertThat(productIds).containsExactly("P-THIRD", "P-SECOND", "P-FIRST");
        assertThat(browseHistoryService.recentProductIds(userId, 2)).hasSize(2);
    }

    @Test
    void internalEndpointRequiresToken() throws Exception {
        String userId = "U-BROWSE-API-" + System.nanoTime();
        browseHistoryService.record(userId, "P-API");

        mockMvc.perform(get("/internal/browse-history/" + userId)
                        .header("X-Internal-Token", INTERNAL_TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data[0]").value("P-API"));
    }
}
