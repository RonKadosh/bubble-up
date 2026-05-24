package com.ronkadosh.studybuddy.common.security;

import com.ronkadosh.studybuddy.support.IntegrationTest;
import org.junit.jupiter.api.Test;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Regression: missing or invalid JWT on a secured endpoint must return 401, not 403.
 * The frontend axios interceptor refreshes only on 401; 403 is reserved for "authenticated
 * but not allowed" (NOT_GROUP_MEMBER, NOT_GROUP_OWNER, etc.).
 */
class AuthEntryPointIT extends IntegrationTest {

    @Test
    void no_bearer_token_returns_401() throws Exception {
        mvc.perform(get("/api/groups"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void invalid_bearer_token_returns_401() throws Exception {
        mvc.perform(get("/api/groups").header("Authorization", "Bearer not.a.real.jwt"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void bearer_without_prefix_returns_401() throws Exception {
        mvc.perform(get("/api/groups").header("Authorization", "garbage"))
                .andExpect(status().isUnauthorized());
    }
}
