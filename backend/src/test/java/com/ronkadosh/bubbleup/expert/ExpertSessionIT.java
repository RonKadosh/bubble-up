package com.ronkadosh.bubbleup.expert;

import com.fasterxml.jackson.databind.JsonNode;
import com.ronkadosh.bubbleup.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ExpertSessionIT extends IntegrationTest {

    @Test
    void verified_expert_creates_session_and_room_is_wired() throws Exception {
        AuthedUser expert = registerEnrolled();
        applyAsExpert(expert);

        String body = sessionBody("Linear Algebra Q&A",
                Instant.now().plus(Duration.ofHours(1)),
                Instant.now().plus(Duration.ofHours(2)),
                3);

        mvc.perform(post("/api/expert-sessions")
                        .with(bearer(expert))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.expertUserId").value(expert.id().toString()))
                .andExpect(jsonPath("$.data.title").value("Linear Algebra Q&A"))
                .andExpect(jsonPath("$.data.status").value("OPEN"))
                .andExpect(jsonPath("$.data.capacity").value(3))
                .andExpect(jsonPath("$.data.enrolledGroupCount").value(0))
                .andExpect(jsonPath("$.data.roomId").isString());
    }

    @Test
    void create_session_without_expert_profile_returns_404() throws Exception {
        AuthedUser u = registerEnrolled();
        mvc.perform(post("/api/expert-sessions")
                        .with(bearer(u))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(sessionBody("x",
                                Instant.now().plus(Duration.ofHours(1)),
                                Instant.now().plus(Duration.ofHours(2)),
                                3)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("EXPERT_PROFILE_NOT_FOUND"));
    }

    @Test
    void create_session_with_inverted_time_range_returns_400() throws Exception {
        AuthedUser expert = registerEnrolled();
        applyAsExpert(expert);
        mvc.perform(post("/api/expert-sessions")
                        .with(bearer(expert))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(sessionBody("x",
                                Instant.now().plus(Duration.ofHours(2)),
                                Instant.now().plus(Duration.ofHours(1)),
                                3)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_EVENT_TIME_RANGE"));
    }

    @Test
    void group_owner_enrolls_group_and_count_updates() throws Exception {
        AuthedUser expert = registerEnrolled();
        applyAsExpert(expert);
        UUID sessionId = createSession(expert, 3);

        AuthedUser groupOwner = registerEnrolled();
        UUID groupId = createGroup(groupOwner);

        mvc.perform(post("/api/expert-sessions/{id}/enroll", sessionId)
                        .with(bearer(groupOwner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"groupId\":\"" + groupId + "\"}"))
                .andExpect(status().isCreated());

        mvc.perform(get("/api/expert-sessions/{id}", sessionId).with(bearer(expert)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.enrolledGroupCount").value(1));
    }

    @Test
    void enroll_same_group_twice_returns_409() throws Exception {
        AuthedUser expert = registerEnrolled();
        applyAsExpert(expert);
        UUID sessionId = createSession(expert, 3);

        AuthedUser groupOwner = registerEnrolled();
        UUID groupId = createGroup(groupOwner);

        enrollGroup(groupOwner, sessionId, groupId).andExpect(status().isCreated());
        enrollGroup(groupOwner, sessionId, groupId)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("EXPERT_SESSION_GROUP_ALREADY_ENROLLED"));
    }

    @Test
    void enroll_by_non_owner_returns_403() throws Exception {
        AuthedUser expert = registerEnrolled();
        applyAsExpert(expert);
        UUID sessionId = createSession(expert, 3);

        AuthedUser groupOwner = registerEnrolled();
        UUID groupId = createGroup(groupOwner);

        AuthedUser stranger = registerEnrolled();
        mvc.perform(post("/api/expert-sessions/{id}/enroll", sessionId)
                        .with(bearer(stranger))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"groupId\":\"" + groupId + "\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("NOT_GROUP_OWNER"));
    }

    @Test
    void capacity_reached_blocks_further_enroll_and_flips_status_to_FULL() throws Exception {
        AuthedUser expert = registerEnrolled();
        applyAsExpert(expert);
        UUID sessionId = createSession(expert, 1);

        AuthedUser owner1 = registerEnrolled();
        UUID group1 = createGroup(owner1);
        enrollGroup(owner1, sessionId, group1).andExpect(status().isCreated());

        mvc.perform(get("/api/expert-sessions/{id}", sessionId).with(bearer(expert)))
                .andExpect(jsonPath("$.data.status").value("FULL"));

        AuthedUser owner2 = registerEnrolled();
        UUID group2 = createGroup(owner2);
        enrollGroup(owner2, sessionId, group2)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("EXPERT_SESSION_CAPACITY_REACHED"));
    }

    @Test
    void unenroll_returns_status_to_OPEN() throws Exception {
        AuthedUser expert = registerEnrolled();
        applyAsExpert(expert);
        UUID sessionId = createSession(expert, 1);

        AuthedUser owner = registerEnrolled();
        UUID groupId = createGroup(owner);
        enrollGroup(owner, sessionId, groupId).andExpect(status().isCreated());

        mvc.perform(post("/api/expert-sessions/{id}/unenroll", sessionId)
                        .with(bearer(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"groupId\":\"" + groupId + "\"}"))
                .andExpect(status().isOk());

        mvc.perform(get("/api/expert-sessions/{id}", sessionId).with(bearer(expert)))
                .andExpect(jsonPath("$.data.status").value("OPEN"))
                .andExpect(jsonPath("$.data.enrolledGroupCount").value(0));
    }

    @Test
    void cancel_session_only_by_host() throws Exception {
        AuthedUser expert = registerEnrolled();
        applyAsExpert(expert);
        UUID sessionId = createSession(expert, 3);

        AuthedUser otherExpert = registerEnrolled();
        applyAsExpert(otherExpert);
        mvc.perform(post("/api/expert-sessions/{id}/cancel", sessionId).with(bearer(otherExpert)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("EXPERT_SESSION_NOT_HOST"));

        mvc.perform(post("/api/expert-sessions/{id}/cancel", sessionId).with(bearer(expert)))
                .andExpect(status().isOk());

        mvc.perform(get("/api/expert-sessions/{id}", sessionId).with(bearer(expert)))
                .andExpect(jsonPath("$.data.status").value("CANCELLED"));
    }

    @Test
    void grant_whiteboard_write_by_non_host_returns_403() throws Exception {
        AuthedUser expert = registerEnrolled();
        applyAsExpert(expert);
        UUID sessionId = createSession(expert, 3);

        AuthedUser other = registerEnrolled();
        mvc.perform(post("/api/expert-sessions/{id}/whiteboard/grant", sessionId)
                        .with(bearer(other))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userId\":\"" + other.id() + "\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("EXPERT_SESSION_NOT_HOST"));
    }

    @Test
    void grant_and_revoke_whiteboard_write_by_host() throws Exception {
        AuthedUser expert = registerEnrolled();
        applyAsExpert(expert);
        UUID sessionId = createSession(expert, 3);

        AuthedUser participant = registerEnrolled();
        mvc.perform(post("/api/expert-sessions/{id}/whiteboard/grant", sessionId)
                        .with(bearer(expert))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userId\":\"" + participant.id() + "\"}"))
                .andExpect(status().isOk());
        mvc.perform(post("/api/expert-sessions/{id}/whiteboard/revoke", sessionId)
                        .with(bearer(expert))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userId\":\"" + participant.id() + "\"}"))
                .andExpect(status().isOk());
    }

    @Test
    void list_mine_returns_host_sessions() throws Exception {
        AuthedUser expert = registerEnrolled();
        applyAsExpert(expert);
        createSession(expert, 3);
        createSession(expert, 5);
        mvc.perform(get("/api/expert-sessions/mine").with(bearer(expert)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(2));
    }

    @Test
    void list_enrolled_returns_sessions_for_group() throws Exception {
        AuthedUser expert = registerEnrolled();
        applyAsExpert(expert);
        UUID sessionId = createSession(expert, 3);

        AuthedUser owner = registerEnrolled();
        UUID groupId = createGroup(owner);
        enrollGroup(owner, sessionId, groupId).andExpect(status().isCreated());

        mvc.perform(get("/api/expert-sessions/enrolled?groupId=" + groupId).with(bearer(owner)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].id").value(sessionId.toString()));
    }

    @Test
    void host_can_enter_own_room_before_open_window() throws Exception {
        AuthedUser expert = registerEnrolled();
        applyAsExpert(expert);
        // Schedule far in the future — well outside the 15-min open window.
        Instant start = Instant.now().plus(Duration.ofHours(5));
        Instant end = start.plus(Duration.ofHours(1));
        String json = mvc.perform(post("/api/expert-sessions")
                        .with(bearer(expert))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(sessionBody("Far future", start, end, 3)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        UUID roomId = UUID.fromString(om.readTree(json).get("data").get("roomId").asText());
        // Host should be past the requireOpen gate. In the test env Jitsi isn't
        // configured so the response 500s with JITSI_NOT_CONFIGURED — that's
        // proof that requireOpen was bypassed (otherwise it would 403 with
        // ROOM_NOT_YET_OPEN before reaching the Jitsi token call).
        mvc.perform(get("/api/rooms/{id}", roomId).with(bearer(expert)))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.error.code").value("JITSI_NOT_CONFIGURED"));
    }

    @Test
    void non_host_cannot_enter_expert_room_before_open_window() throws Exception {
        AuthedUser expert = registerEnrolled();
        applyAsExpert(expert);
        Instant start = Instant.now().plus(Duration.ofHours(5));
        Instant end = start.plus(Duration.ofHours(1));
        String json = mvc.perform(post("/api/expert-sessions")
                        .with(bearer(expert))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(sessionBody("Far future", start, end, 3)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        UUID sessionId = UUID.fromString(om.readTree(json).get("data").get("id").asText());
        UUID roomId = UUID.fromString(om.readTree(json).get("data").get("roomId").asText());

        AuthedUser owner = registerEnrolled();
        UUID groupId = createGroup(owner);
        enrollGroup(owner, sessionId, groupId).andExpect(status().isCreated());

        mvc.perform(get("/api/rooms/{id}", roomId).with(bearer(owner)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("EXPERT_SESSION_NOT_OPEN_FOR_JOIN_YET"));
    }

    @Test
    void list_open_returns_only_OPEN_sessions_sorted_by_start() throws Exception {
        AuthedUser expert = registerEnrolled();
        applyAsExpert(expert);
        UUID openSessionId = createSession(expert, 5);
        UUID smallSessionId = createSession(expert, 1);

        // Fill the second session so it flips to FULL and drops out of listOpen.
        AuthedUser owner = registerEnrolled();
        UUID groupId = createGroup(owner);
        enrollGroup(owner, smallSessionId, groupId).andExpect(status().isCreated());

        // Data from other tests in this class accumulates in the H2 schema;
        // assert membership rather than a fixed list length.
        AuthedUser viewer = registerEnrolled();
        mvc.perform(get("/api/expert-sessions/open").with(bearer(viewer)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[?(@.id=='" + openSessionId + "')]").exists())
                .andExpect(jsonPath("$.data[?(@.id=='" + smallSessionId + "')]").doesNotExist())
                .andExpect(jsonPath("$.data[?(@.status!='OPEN')]").doesNotExist());
    }

    @Test
    void list_open_for_expert_returns_only_that_experts_open_sessions() throws Exception {
        AuthedUser expert = registerEnrolled();
        applyAsExpert(expert);
        UUID openSessionId = createSession(expert, 5);
        UUID fullSessionId = createSession(expert, 1);

        AuthedUser otherExpert = registerEnrolled();
        applyAsExpert(otherExpert);
        UUID otherSessionId = createSession(otherExpert, 5);

        AuthedUser owner = registerEnrolled();
        UUID groupId = createGroup(owner);
        enrollGroup(owner, fullSessionId, groupId).andExpect(status().isCreated());

        AuthedUser viewer = registerEnrolled();
        mvc.perform(get("/api/expert-sessions/by-expert/{expertUserId}", expert.id()).with(bearer(viewer)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[?(@.id=='" + openSessionId + "')]").exists())
                .andExpect(jsonPath("$.data[?(@.id=='" + fullSessionId + "')]").doesNotExist())
                .andExpect(jsonPath("$.data[?(@.id=='" + otherSessionId + "')]").doesNotExist())
                .andExpect(jsonPath("$.data[?(@.status!='OPEN')]").doesNotExist());
    }

    @Test
    void group_calendar_includes_enrolled_session_events() throws Exception {
        AuthedUser expert = registerEnrolled();
        applyAsExpert(expert);
        UUID sessionId = createSession(expert, 3);

        AuthedUser owner = registerEnrolled();
        UUID groupId = createGroup(owner);
        enrollGroup(owner, sessionId, groupId).andExpect(status().isCreated());

        // Wide window so the start of the session (1h ahead) is included
        // regardless of when the test runs.
        String from = Instant.now().minus(Duration.ofDays(1)).toString();
        String to = Instant.now().plus(Duration.ofDays(7)).toString();
        mvc.perform(get("/api/calendars/events?ownerType=GROUP&ownerId={g}&from={f}&to={t}",
                        groupId, from, to)
                        .with(bearer(owner)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(org.hamcrest.Matchers.greaterThanOrEqualTo(1)))
                .andExpect(jsonPath("$.data[?(@.eventType=='EXPERT_SESSION')]").exists());
    }

    // --- helpers ---

    private void applyAsExpert(AuthedUser u) throws Exception {
        mvc.perform(post("/api/experts/apply")
                        .with(bearer(u))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"headline\":\"TA\"}"))
                .andExpect(status().isCreated());
    }

    private UUID createSession(AuthedUser expert, int capacity) throws Exception {
        Instant start = Instant.now().plus(Duration.ofHours(1));
        Instant end = start.plus(Duration.ofHours(1));
        String json = mvc.perform(post("/api/expert-sessions")
                        .with(bearer(expert))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(sessionBody("Session", start, end, capacity)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        JsonNode data = om.readTree(json).get("data");
        return UUID.fromString(data.get("id").asText());
    }

    private UUID createGroup(AuthedUser owner) throws Exception {
        String json = mvc.perform(post("/api/groups")
                        .with(bearer(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"g\",\"maxMembers\":6,\"offeringId\":\"" + seedOfferingId() + "\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return UUID.fromString(om.readTree(json).get("data").get("id").asText());
    }

    private org.springframework.test.web.servlet.ResultActions enrollGroup(
            AuthedUser owner, UUID sessionId, UUID groupId) throws Exception {
        return mvc.perform(post("/api/expert-sessions/{id}/enroll", sessionId)
                .with(bearer(owner))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"groupId\":\"" + groupId + "\"}"));
    }

    private String sessionBody(String title, Instant start, Instant end, int capacity) {
        return String.format(
                "{\"title\":\"%s\",\"startsAt\":\"%s\",\"endsAt\":\"%s\",\"capacity\":%d}",
                title, start.toString(), end.toString(), capacity);
    }
}
