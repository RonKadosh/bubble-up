package com.ronkadosh.bubbleup.e2e;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * FLOWS §13 (Admin hub): overview KPIs, user search + role change, expert
 * verification (verify/revoke), groups + quiz management. The non-admin guard
 * is the {@code RequireAdmin} route gate's server-side counterpart.
 */
class AdminJourneyE2EIT extends E2EFlowTest {

    @Test
    void overview_kpis_load() throws Exception {
        AuthedUser admin = registerAndLoginAsAdmin();
        mvc.perform(get("/api/admin/overview").with(bearer(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.kpis.totalUsers").exists())
                .andExpect(jsonPath("$.data.roleDistribution").exists());
    }

    @Test
    void search_users_then_change_a_role() throws Exception {
        AuthedUser admin = registerAndLoginAsAdmin();
        AuthedUser target = registerAndLogin();

        // Users tab search by email → the page response shape.
        mvc.perform(get("/api/admin/users").param("q", target.email()).with(bearer(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[?(@.id == '" + target.id() + "')]").exists());

        // Row → modal → Change role.
        mvc.perform(patch("/api/admin/users/{id}/role", target.id())
                        .with(bearer(admin)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"newRole\":\"EXPERT\",\"reason\":\"promoting a TA\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.role").value("EXPERT"));
    }

    @Test
    void expert_verification_verify_then_revoke() throws Exception {
        AuthedUser admin = registerAndLoginAsAdmin();

        // A user applies — v1 auto-verifies, so they show under VERIFIED.
        AuthedUser applicant = registerEnrolled();
        applyAsExpert(applicant, "Applicant " + unique());
        mvc.perform(get("/api/admin/experts").param("status", "VERIFIED").with(bearer(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[?(@.userId == '" + applicant.id() + "')]").exists());

        // Revoke clears the verified flag back to PENDING (keeps the profile + EXPERT role).
        mvc.perform(post("/api/admin/experts/{uid}/revoke", applicant.id())
                        .with(bearer(admin)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"stale credentials\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.verificationStatus").value("PENDING"));

        // Re-verify from the detail modal.
        mvc.perform(post("/api/admin/experts/{uid}/verify", applicant.id())
                        .with(bearer(admin)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"rechecked\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.verificationStatus").value("VERIFIED"));
    }

    @Test
    void groups_tab_search_lists_a_created_bubble() throws Exception {
        AuthedUser admin = registerAndLoginAsAdmin();
        AuthedUser owner = registerEnrolled();
        String name = "AdminGroups " + unique();
        UUID groupId = createPublicBubble(owner, name);

        mvc.perform(get("/api/admin/groups").param("q", name).with(bearer(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[?(@.id == '" + groupId + "')]").exists());
    }

    @Test
    void quiz_tab_creates_a_question_with_an_option() throws Exception {
        AuthedUser admin = registerAndLoginAsAdmin();

        UUID questionId = UUID.fromString(dataOf(mvc.perform(post("/api/admin/quiz/questions")
                        .with(bearer(admin)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"textEn\":\"How do you study?\",\"active\":true}"))
                .andExpect(status().isCreated())).get("id").asText());

        mvc.perform(post("/api/admin/quiz/questions/{qid}/options", questionId)
                        .with(bearer(admin)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"textEn\":\"Alone\",\"weightPlanner\":3}"))
                .andExpect(status().isCreated());

        // Quiz list reflects the new question.
        mvc.perform(get("/api/admin/quiz/questions").with(bearer(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[?(@.question.id == '" + questionId + "')]").exists());
    }

    @Test
    void catalog_universities_list_loads() throws Exception {
        AuthedUser admin = registerAndLoginAsAdmin();
        JsonNode unis = getData(admin, "/api/admin/catalog/universities");
        assertThat(unis.isArray()).isTrue();
    }

    @Test
    void non_admin_is_forbidden() throws Exception {
        AuthedUser plain = registerAndLogin();
        mvc.perform(get("/api/admin/overview").with(bearer(plain)))
                .andExpect(status().isForbidden());
    }
}
