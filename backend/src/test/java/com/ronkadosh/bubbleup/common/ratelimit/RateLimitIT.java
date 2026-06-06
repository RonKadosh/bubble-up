package com.ronkadosh.bubbleup.common.ratelimit;

import com.ronkadosh.bubbleup.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Verifies {@code @RateLimit} enforcement end-to-end through the MVC stack. Opts
 * into rate limiting (the suite runs with it OFF — see application-test.yml) so
 * this is the one place the limiter is actually exercised.
 *
 * <p>Target endpoint is {@code PATCH /api/users/me/profile} (scope PER_USER,
 * limit 20/60s) — the cheapest annotated write to drive: it needs only a logged-in
 * user, no group/enrollment, and an empty body is a valid no-op patch.
 *
 * <p>We assert the enforce + per-user-isolation paths. The window-reset path is not
 * asserted here: the limiter uses the {@code @Primary SystemTimeProvider}, so a
 * deterministic clock advance would require globally swapping the primary clock —
 * not worth destabilizing the rest of the suite. Reset is covered by manual testing
 * (see the plan's verification section).
 */
@TestPropertySource(properties = "app.rate-limit.enabled=true")
class RateLimitIT extends IntegrationTest {

    /** PER_USER limit on PATCH /users/me/profile, mirrored from the annotation. */
    private static final int LIMIT = 20;

    private void patchProfile(AuthedUser u, org.springframework.test.web.servlet.ResultMatcher expected) throws Exception {
        mvc.perform(patch("/api/users/me/profile")
                        .with(bearer(u))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(expected);
    }

    @Test
    void exceedingLimitReturns429WithCodeAndRetryAfter() throws Exception {
        AuthedUser u = registerAndLogin();

        // The limit'th request is still allowed.
        for (int i = 0; i < LIMIT; i++) {
            patchProfile(u, status().isOk());
        }

        // The next one is rejected with the standard envelope + Retry-After header.
        mvc.perform(patch("/api/users/me/profile")
                        .with(bearer(u))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("TOO_MANY_REQUESTS"))
                .andExpect(jsonPath("$.error.category").value("RATE_LIMIT"))
                .andExpect(header().exists("Retry-After"));
    }

    @Test
    void limitIsScopedPerUser() throws Exception {
        AuthedUser a = registerAndLogin();
        AuthedUser b = registerAndLogin();

        // Exhaust user A's bucket.
        for (int i = 0; i < LIMIT; i++) {
            patchProfile(a, status().isOk());
        }
        patchProfile(a, status().isTooManyRequests());

        // User B has its own bucket — unaffected.
        patchProfile(b, status().isOk());
    }
}
