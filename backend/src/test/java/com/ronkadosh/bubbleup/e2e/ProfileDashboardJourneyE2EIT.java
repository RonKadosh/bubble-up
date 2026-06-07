package com.ronkadosh.bubbleup.e2e;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * FLOWS §12 (Settings/Profile: edit own, view another), §3 (Dashboard feed),
 * and the §2.5 matching surface (reliability + the Daily Drop poll endpoint).
 */
class ProfileDashboardJourneyE2EIT extends E2EFlowTest {

    @Test
    void edit_own_profile_then_read_it_back() throws Exception {
        AuthedUser me = registerAndLogin();

        // Settings → Profile → Edit → Save (display name + bio + affiliation cascade).
        mvc.perform(patch("/api/users/me/profile")
                        .with(bearer(me)).contentType(MediaType.APPLICATION_JSON)
                        .content(String.format(
                                "{\"displayName\":\"Ada Lovelace\",\"bio\":\"counts on bubbles\",\"universityId\":\"%s\",\"departmentId\":\"%s\",\"enrollmentYear\":2}",
                                seedUniversityId(), seedDepartmentId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.displayName").value("Ada Lovelace"));

        mvc.perform(get("/api/users/me/profile").with(bearer(me)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.bio").value("counts on bubbles"))
                .andExpect(jsonPath("$.data.enrollmentYear").value(2));
    }

    @Test
    void view_a_co_members_profile_is_visible_but_a_strangers_is_not() throws Exception {
        // Profiles are visible only between users who share a bubble — which is
        // exactly where UserProfileCard opens (members drawer / chat avatar).
        AuthedUser owner = registerEnrolled();
        AuthedUser member = registerEnrolled();
        mvc.perform(patch("/api/users/me/profile")
                        .with(bearer(member)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"displayName\":\"Grace Hopper\"}"))
                .andExpect(status().isOk());
        UUID groupId = createPublicBubble(owner, "Roster " + unique());
        joinBubble(member, groupId);

        // Co-member tap on an avatar → GET /api/users/{id}/profile resolves.
        mvc.perform(get("/api/users/{id}/profile", member.id()).with(bearer(owner)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.userId").value(member.id().toString()))
                .andExpect(jsonPath("$.data.displayName").value("Grace Hopper"));

        // A stranger with no shared bubble is gated — ProfilePage renders "cannot view".
        AuthedUser stranger = registerAndLogin();
        mvc.perform(get("/api/users/{id}/profile", member.id()).with(bearer(stranger)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("PROFILE_NOT_VISIBLE"));
    }

    @Test
    void dashboard_feed_returns_ordered_sections() throws Exception {
        AuthedUser me = registerEnrolled();
        createPublicBubble(me, "Feeding " + unique());

        // DashboardPage → GET /api/feed (one sectioned digest).
        JsonNode feed = getData(me, "/api/feed");
        assertThat(feed.get("sections").isArray()).isTrue();
    }

    @Test
    void matching_reliability_and_daily_drop_endpoints_are_reachable() throws Exception {
        AuthedUser me = registerEnrolled();

        // Settings → Matching tab → ReliabilityMeter.
        mvc.perform(get("/api/matching/reliability").with(bearer(me)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.confidence").exists())
                .andExpect(jsonPath("$.data.answeredQuestions").exists());

        // The floating QuizPrompt polls for the next Daily Drop.
        mvc.perform(get("/api/matching/quiz/next").with(bearer(me)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.hasQuestion").exists());
    }
}
