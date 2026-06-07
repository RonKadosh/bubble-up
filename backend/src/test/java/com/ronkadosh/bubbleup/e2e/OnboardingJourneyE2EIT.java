package com.ronkadosh.bubbleup.e2e;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * FLOWS §1 (Auth) + §2 (Onboarding wizard) + §2.4 ("Find a Bubble").
 *
 * Walks the gated wizard the way the dashboard does: every step's gate is derived
 * live from {@code GET /api/onboarding/status}, so this drives the real
 * register → affiliate → enroll → in-a-bubble progression that flips the locks.
 */
class OnboardingJourneyE2EIT extends E2EFlowTest {

    @Test
    void full_wizard_progression_register_to_complete() throws Exception {
        // ── L1: register (the only thing the login screen does on success) ──
        AuthedUser u = registerAndLogin();

        JsonNode s0 = getData(u, "/api/onboarding/status");
        assertThat(s0.get("complete").asBoolean()).isFalse();
        assertThat(s0.get("studyBase").get("affiliationDone").asBoolean()).isFalse();
        assertThat(s0.get("studyBase").get("coursesDone").asBoolean()).isFalse();
        assertThat(s0.get("inBubble").asBoolean()).isFalse();
        assertThat(s0.get("wizardLevel").asInt()).isEqualTo(1);

        // ── L2: profile step → PATCH /api/users/me/profile sets affiliation ──
        setAffiliation(u, seedUniversityId(), seedDepartmentId());
        assertThat(getData(u, "/api/onboarding/status")
                .get("studyBase").get("affiliationDone").asBoolean()).isTrue();

        // ── L3: enroll step → POST /api/enrollments ──
        enroll(u, seedOfferingCourseId());
        JsonNode s2 = getData(u, "/api/onboarding/status");
        assertThat(s2.get("studyBase").get("coursesDone").asBoolean()).isTrue();
        assertThat(s2.get("inBubble").asBoolean()).isFalse();
        assertThat(s2.get("complete").asBoolean()).isFalse();

        // Wizard "Next" persists the level as the user advances (PUT /api/onboarding/level).
        mvc.perform(put("/api/onboarding/level")
                        .with(bearer(u))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"level\":4}"))
                .andExpect(status().isOk());

        // ── L4: bubble step → create a Bubble flips inBubble + completes setup ──
        createPublicBubble(u, "Onboarding " + unique());
        JsonNode s3 = getData(u, "/api/onboarding/status");
        assertThat(s3.get("inBubble").asBoolean()).isTrue();
        assertThat(s3.get("complete").asBoolean()).isTrue();
        assertThat(s3.get("wizardLevel").asInt()).as("level survived the round-trip").isEqualTo(4);
    }

    @Test
    void find_a_bubble_step_lists_then_joins_a_discoverable_bubble() throws Exception {
        // Someone else owns a public bubble in the seed course.
        AuthedUser owner = registerEnrolled();
        String name = "Findable " + unique();
        UUID groupId = createPublicBubble(owner, name);

        // A fresh enrolled user runs the L4 "Find a Bubble" panel: GET /api/groups/discoverable.
        AuthedUser me = registerEnrolled();
        mvc.perform(get("/api/groups/discoverable").with(bearer(me)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[?(@.name == '" + name + "')]").exists());

        // Join it → it drops out of discovery (you're now a member) and onboarding completes.
        joinBubble(me, groupId);
        mvc.perform(get("/api/groups/discoverable").with(bearer(me)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[?(@.name == '" + name + "')]").doesNotExist());
        assertThat(getData(me, "/api/onboarding/status").get("inBubble").asBoolean()).isTrue();
    }

    @Test
    void acknowledgements_and_collapse_preference_persist() throws Exception {
        AuthedUser u = registerAndLogin();

        mvc.perform(post("/api/onboarding/ack")
                        .with(bearer(u)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"key\":\"guide:enroll\"}"))
                .andExpect(status().isOk());
        mvc.perform(put("/api/onboarding/collapsed")
                        .with(bearer(u)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"collapsed\":true}"))
                .andExpect(status().isOk());

        JsonNode s = getData(u, "/api/onboarding/status");
        assertThat(s.get("collapsed").asBoolean()).isTrue();
        boolean hasGuide = false;
        for (JsonNode k : s.get("acknowledged")) hasGuide |= k.asText().equals("guide:enroll");
        assertThat(hasGuide).isTrue();
    }

    @Test
    void status_requires_authentication() throws Exception {
        mvc.perform(get("/api/onboarding/status")).andExpect(status().isUnauthorized());
    }
}
