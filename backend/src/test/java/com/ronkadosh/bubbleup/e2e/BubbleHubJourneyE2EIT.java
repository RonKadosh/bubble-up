package com.ronkadosh.bubbleup.e2e;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * FLOWS §5 (Bubble hub: create / sidebar / members / info drawer), §4.5–4.6
 * (course page gated browse + join), §3.4 (discovery preview → join).
 *
 * The whole membership lifecycle a hub user drives: create → others join/added →
 * owner can't abandon a populated bubble → transfer → leave → remove → pop.
 */
class BubbleHubJourneyE2EIT extends E2EFlowTest {

    @Test
    void create_bubble_shows_in_my_bubbles_and_auto_creates_general_room() throws Exception {
        AuthedUser owner = registerEnrolled();
        String name = "Hub " + unique();
        UUID groupId = createPublicBubble(owner, name);

        // Sidebar "My Bubbles" → GET /api/groups/me (per-user scoped, safe to inspect).
        JsonNode mine = getData(owner, "/api/groups/me");
        boolean present = false;
        for (JsonNode g : mine) present |= g.get("id").asText().equals(groupId.toString());
        assertThat(present).isTrue();

        // ChatPanel assumes the auto-created "general" room exists.
        JsonNode rooms = getData(owner, "/api/chat/rooms");
        assertThat(rooms).hasSize(1);
        assertThat(rooms.get(0).get("name").asText()).isEqualTo("general");
        assertThat(rooms.get(0).get("groupId").asText()).isEqualTo(groupId.toString());
    }

    @Test
    void course_page_is_gated_until_enrolled_then_lists_groups() throws Exception {
        // A public bubble exists in the seed course.
        AuthedUser owner = registerEnrolled();
        String name = "Course " + unique();
        createPublicBubble(owner, name);

        // Affiliated but NOT enrolled → CoursePage shows the GatedCard (403).
        AuthedUser visitor = registerWithAffiliation();
        mvc.perform(get("/api/groups/by-course/{c}", seedOfferingCourseId()).with(bearer(visitor)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("NOT_ENROLLED_IN_COURSE"));

        // Enroll (the gated card's "Enroll" button) → the groups list opens up.
        enroll(visitor, seedOfferingCourseId());
        mvc.perform(get("/api/groups/by-course/{c}", seedOfferingCourseId()).with(bearer(visitor)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[?(@.name == '" + name + "')]").exists());
    }

    @Test
    void by_course_filters_narrow_the_list() throws Exception {
        AuthedUser owner = registerEnrolled();
        String pub = "Pub " + unique();
        String priv = "Priv " + unique();
        createBubble(owner, pub, "PUBLIC");
        createBubble(owner, priv, "PRIVATE");

        // visibility=PUBLIC filter excludes the private one.
        mvc.perform(get("/api/groups/by-course/{c}", seedOfferingCourseId())
                        .param("visibility", "PUBLIC").with(bearer(owner)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[?(@.name == '" + pub + "')]").exists())
                .andExpect(jsonPath("$.data[?(@.name == '" + priv + "')]").doesNotExist());

        // q search narrows to the matching name.
        mvc.perform(get("/api/groups/by-course/{c}", seedOfferingCourseId())
                        .param("q", priv).with(bearer(owner)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[?(@.name == '" + priv + "')]").exists())
                .andExpect(jsonPath("$.data[?(@.name == '" + pub + "')]").doesNotExist());
    }

    @Test
    void discovery_preview_then_join_lands_member_in_the_bubble() throws Exception {
        AuthedUser owner = registerEnrolled();
        UUID groupId = createPublicBubble(owner, "Discover " + unique());

        // Dashboard discovery card → PublicBubbleModal fetches GET /api/groups/{id}.
        AuthedUser me = registerEnrolled();
        mvc.perform(get("/api/groups/{id}", groupId).with(bearer(me)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.visibility").value("PUBLIC"))
                .andExpect(jsonPath("$.data.memberCount").value(1));

        // "Join" in the modal → membership grows to 2.
        joinBubble(me, groupId);
        mvc.perform(get("/api/groups/{id}/members", groupId).with(bearer(me)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(2));
    }

    @Test
    void membership_lifecycle_add_transfer_leave_remove_pop() throws Exception {
        AuthedUser owner = registerEnrolled();
        AuthedUser joiner = registerEnrolled();
        AuthedUser added = registerEnrolled();
        UUID groupId = createPublicBubble(owner, "Lifecycle " + unique());

        // GroupHeader "Hop in".
        joinBubble(joiner, groupId);
        // BubbleInfoDrawer owner-only "Add member by id".
        mvc.perform(post("/api/groups/{id}/members", groupId)
                        .with(bearer(owner)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userId\":\"" + added.id() + "\"}"))
                .andExpect(status().isCreated());
        mvc.perform(get("/api/groups/{id}/members", groupId).with(bearer(owner)))
                .andExpect(jsonPath("$.data.length()").value(3));

        // An owner can't walk out on a populated bubble — drawer surfaces this.
        mvc.perform(delete("/api/groups/{id}/members/me", groupId).with(bearer(owner)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("OWNER_MUST_TRANSFER_OR_EMPTY"));

        // "Make owner" on the joiner → ownership flips.
        mvc.perform(post("/api/groups/{id}/transfer-ownership", groupId)
                        .with(bearer(owner)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"newOwnerId\":\"" + joiner.id() + "\"}"))
                .andExpect(status().isOk());
        assertOwnerIs(joiner, groupId, joiner.id());

        // Former owner can now leave; new owner removes the added member.
        mvc.perform(delete("/api/groups/{id}/members/me", groupId).with(bearer(owner)))
                .andExpect(status().isOk());
        mvc.perform(delete("/api/groups/{id}/members/{uid}", groupId, added.id()).with(bearer(joiner)))
                .andExpect(status().isOk());

        // Now solo → owner can "Pop" the empty bubble.
        mvc.perform(delete("/api/groups/{id}", groupId).with(bearer(joiner)))
                .andExpect(status().isOk());
        mvc.perform(get("/api/groups/{id}", groupId).with(bearer(joiner)))
                .andExpect(status().isNotFound());
    }

    @Test
    void non_enrolled_user_cannot_join() throws Exception {
        AuthedUser owner = registerEnrolled();
        UUID groupId = createPublicBubble(owner, "Gated " + unique());

        AuthedUser stranger = registerWithAffiliation(); // affiliated, not enrolled
        mvc.perform(post("/api/groups/{id}/join", groupId).with(bearer(stranger)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("NOT_ENROLLED_IN_COURSE"));
    }

    private void assertOwnerIs(AuthedUser viewer, UUID groupId, UUID expectedOwner) throws Exception {
        JsonNode members = getData(viewer, "/api/groups/" + groupId + "/members");
        String ownerId = null;
        for (JsonNode m : members) if (m.get("role").asText().equals("OWNER")) ownerId = m.get("userId").asText();
        assertThat(ownerId).isEqualTo(expectedOwner.toString());
    }
}
