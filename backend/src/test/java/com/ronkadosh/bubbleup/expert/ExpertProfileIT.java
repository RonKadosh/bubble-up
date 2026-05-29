package com.ronkadosh.bubbleup.expert;

import com.ronkadosh.bubbleup.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ExpertProfileIT extends IntegrationTest {

    @Test
    void apply_creates_verified_profile_and_promotes_user() throws Exception {
        AuthedUser u = registerAndLogin();
        String body = """
                {"headline":"Linear Algebra TA","bio":"6 years TA at BGU",
                 "expertiseTags":["linear-algebra","proofs"]}
                """;

        mvc.perform(post("/api/experts/apply")
                        .with(bearer(u))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.userId").value(u.id().toString()))
                .andExpect(jsonPath("$.data.headline").value("Linear Algebra TA"))
                .andExpect(jsonPath("$.data.verificationStatus").value("VERIFIED"))
                .andExpect(jsonPath("$.data.expertiseTags", org.hamcrest.Matchers.containsInAnyOrder(
                        "linear-algebra", "proofs")));
    }

    @Test
    void apply_twice_returns_conflict() throws Exception {
        AuthedUser u = registerAndLogin();
        applyOnce(u, "TA");
        mvc.perform(post("/api/experts/apply")
                        .with(bearer(u))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"headline\":\"again\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("EXPERT_APPLICATION_ALREADY_SUBMITTED"));
    }

    @Test
    void get_me_returns_profile_after_apply() throws Exception {
        AuthedUser u = registerAndLogin();
        applyOnce(u, "Stats Tutor");
        mvc.perform(get("/api/experts/me").with(bearer(u)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.headline").value("Stats Tutor"));
    }

    @Test
    void get_me_without_profile_returns_404() throws Exception {
        AuthedUser u = registerAndLogin();
        mvc.perform(get("/api/experts/me").with(bearer(u)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("EXPERT_PROFILE_NOT_FOUND"));
    }

    @Test
    void patch_me_updates_headline_and_tags() throws Exception {
        AuthedUser u = registerAndLogin();
        applyOnce(u, "Old headline");
        mvc.perform(patch("/api/experts/me")
                        .with(bearer(u))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"headline\":\"New headline\",\"expertiseTags\":[\"calc\"]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.headline").value("New headline"))
                .andExpect(jsonPath("$.data.expertiseTags[0]").value("calc"));
    }

    @Test
    void get_public_profile_returns_other_users_profile() throws Exception {
        AuthedUser expert = registerAndLogin();
        applyOnce(expert, "Public Expert");
        AuthedUser viewer = registerAndLogin();
        mvc.perform(get("/api/experts/{id}", expert.id()).with(bearer(viewer)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.userId").value(expert.id().toString()))
                .andExpect(jsonPath("$.data.headline").value("Public Expert"));
    }

    @Test
    void get_public_profile_for_non_expert_returns_404() throws Exception {
        AuthedUser viewer = registerAndLogin();
        mvc.perform(get("/api/experts/{id}", UUID.randomUUID()).with(bearer(viewer)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("EXPERT_PROFILE_NOT_FOUND"));
    }

    @Test
    void directory_lists_verified_experts() throws Exception {
        AuthedUser e1 = registerAndLogin();
        applyOnce(e1, "Expert One");
        AuthedUser e2 = registerAndLogin();
        applyOnce(e2, "Expert Two");

        AuthedUser viewer = registerAndLogin();
        mvc.perform(get("/api/experts/directory").with(bearer(viewer)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(
                        org.hamcrest.Matchers.greaterThanOrEqualTo(2)));
    }

    @Test
    void apply_requires_auth() throws Exception {
        mvc.perform(post("/api/experts/apply")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"headline\":\"x\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void apply_rejects_blank_headline() throws Exception {
        AuthedUser u = registerAndLogin();
        mvc.perform(post("/api/experts/apply")
                        .with(bearer(u))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"headline\":\"\"}"))
                .andExpect(status().isBadRequest());
    }

    private void applyOnce(AuthedUser u, String headline) throws Exception {
        String body = String.format("{\"headline\":\"%s\"}", headline);
        mvc.perform(post("/api/experts/apply")
                        .with(bearer(u))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated());
    }
}
