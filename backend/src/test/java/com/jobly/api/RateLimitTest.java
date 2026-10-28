package com.jobly.api;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.TestPropertySource;

import com.jobly.support.IntegrationTest;

/**
 * The per-IP limit on the public account endpoints (here lowered to 3 so it is easy to reach).
 * حد الطلبات لكل IP على روابط الحسابات العامة (مخفّض هنا إلى 3 حتى يسهل الوصول له).
 */
@TestPropertySource(properties = "jobly.rate-limit.attempts=3")
class RateLimitTest extends IntegrationTest {

    @Test
    @DisplayName("the 4th request in the window gets 429 with Retry-After, on any limited endpoint")
    void blocksAfterTheLimit() throws Exception {
        for (int i = 0; i < 3; i++) {
            loginRequest("nobody@example.com", "Wrong123").andExpect(status().isUnauthorized());
        }
        loginRequest("nobody@example.com", "Wrong123")
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"))
                .andExpect(jsonPath("$.code").value("RATE_LIMITED"));
        // The same address shares one counter across the account endpoints.
        // نفس العنوان له عدّاد واحد مشترك بين روابط الحسابات.
        postJson("/api/auth/forgot-password", "{\"email\":\"nobody@example.com\"}")
                .andExpect(status().isTooManyRequests());
    }
}
