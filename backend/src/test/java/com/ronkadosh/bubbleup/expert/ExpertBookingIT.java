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

class ExpertBookingIT extends IntegrationTest {

    @Test
    void group_owner_can_request_a_booking_with_verified_expert() throws Exception {
        AuthedUser expert = registerEnrolled();
        applyAsExpert(expert);
        AuthedUser owner = registerEnrolled();
        UUID groupId = createGroup(owner);

        mvc.perform(post("/api/expert-bookings")
                        .with(bearer(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody(expert.id(), groupId,
                                Instant.now().plus(Duration.ofDays(2)),
                                Instant.now().plus(Duration.ofDays(2)).plus(Duration.ofHours(1)),
                                "Need help with eigenvalues")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.expertUserId").value(expert.id().toString()))
                .andExpect(jsonPath("$.data.groupId").value(groupId.toString()))
                .andExpect(jsonPath("$.data.status").value("PENDING"))
                .andExpect(jsonPath("$.data.message").value("Need help with eigenvalues"));
    }

    @Test
    void request_against_non_verified_user_returns_403() throws Exception {
        AuthedUser nonExpert = registerEnrolled();
        AuthedUser owner = registerEnrolled();
        UUID groupId = createGroup(owner);
        mvc.perform(post("/api/expert-bookings")
                        .with(bearer(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody(nonExpert.id(), groupId,
                                Instant.now().plus(Duration.ofHours(1)),
                                Instant.now().plus(Duration.ofHours(2)),
                                "")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("EXPERT_NOT_VERIFIED"));
    }

    @Test
    void request_by_non_group_owner_returns_403() throws Exception {
        AuthedUser expert = registerEnrolled();
        applyAsExpert(expert);
        AuthedUser owner = registerEnrolled();
        UUID groupId = createGroup(owner);
        AuthedUser stranger = registerEnrolled();
        mvc.perform(post("/api/expert-bookings")
                        .with(bearer(stranger))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody(expert.id(), groupId,
                                Instant.now().plus(Duration.ofHours(1)),
                                Instant.now().plus(Duration.ofHours(2)),
                                "")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("NOT_GROUP_OWNER"));
    }

    @Test
    void expert_accepts_request_and_session_is_created_with_group_enrolled() throws Exception {
        AuthedUser expert = registerEnrolled();
        applyAsExpert(expert);
        AuthedUser owner = registerEnrolled();
        UUID groupId = createGroup(owner);
        UUID requestId = createBookingRequest(owner, expert, groupId);

        mvc.perform(post("/api/expert-bookings/{id}/accept", requestId).with(bearer(expert)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("ACCEPTED"))
                .andExpect(jsonPath("$.data.acceptedSessionId").isString())
                .andExpect(jsonPath("$.data.decidedAt").isString());

        // Inspect the resulting session: capacity 1, status FULL, group enrolled.
        UUID sessionId = UUID.fromString(om.readTree(
                        mvc.perform(get("/api/expert-bookings/mine?asExpert=true").with(bearer(expert)))
                                .andReturn().getResponse().getContentAsString())
                .get("data").get(0).get("acceptedSessionId").asText());

        mvc.perform(get("/api/expert-sessions/{id}", sessionId).with(bearer(expert)))
                .andExpect(jsonPath("$.data.capacity").value(1))
                .andExpect(jsonPath("$.data.status").value("FULL"))
                .andExpect(jsonPath("$.data.enrolledGroupCount").value(1));
    }

    @Test
    void accept_by_non_addressed_expert_returns_403() throws Exception {
        AuthedUser expert = registerEnrolled();
        applyAsExpert(expert);
        AuthedUser owner = registerEnrolled();
        UUID groupId = createGroup(owner);
        UUID requestId = createBookingRequest(owner, expert, groupId);

        AuthedUser otherExpert = registerEnrolled();
        applyAsExpert(otherExpert);
        mvc.perform(post("/api/expert-bookings/{id}/accept", requestId).with(bearer(otherExpert)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("NOT_BOOKING_REQUEST_EXPERT"));
    }

    @Test
    void accept_already_decided_request_returns_409() throws Exception {
        AuthedUser expert = registerEnrolled();
        applyAsExpert(expert);
        AuthedUser owner = registerEnrolled();
        UUID groupId = createGroup(owner);
        UUID requestId = createBookingRequest(owner, expert, groupId);

        mvc.perform(post("/api/expert-bookings/{id}/accept", requestId).with(bearer(expert)))
                .andExpect(status().isOk());
        mvc.perform(post("/api/expert-bookings/{id}/accept", requestId).with(bearer(expert)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("BOOKING_REQUEST_ALREADY_DECIDED"));
    }

    @Test
    void expert_rejects_request() throws Exception {
        AuthedUser expert = registerEnrolled();
        applyAsExpert(expert);
        AuthedUser owner = registerEnrolled();
        UUID groupId = createGroup(owner);
        UUID requestId = createBookingRequest(owner, expert, groupId);

        mvc.perform(post("/api/expert-bookings/{id}/reject", requestId).with(bearer(expert)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("REJECTED"));
    }

    @Test
    void requester_can_withdraw_pending_request() throws Exception {
        AuthedUser expert = registerEnrolled();
        applyAsExpert(expert);
        AuthedUser owner = registerEnrolled();
        UUID groupId = createGroup(owner);
        UUID requestId = createBookingRequest(owner, expert, groupId);

        mvc.perform(post("/api/expert-bookings/{id}/withdraw", requestId).with(bearer(owner)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("WITHDRAWN"));
    }

    @Test
    void withdraw_by_non_requester_returns_403() throws Exception {
        AuthedUser expert = registerEnrolled();
        applyAsExpert(expert);
        AuthedUser owner = registerEnrolled();
        UUID groupId = createGroup(owner);
        UUID requestId = createBookingRequest(owner, expert, groupId);

        AuthedUser stranger = registerEnrolled();
        mvc.perform(post("/api/expert-bookings/{id}/withdraw", requestId).with(bearer(stranger)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("NOT_BOOKING_REQUEST_REQUESTER"));
    }

    @Test
    void list_mine_returns_inbound_for_expert_and_outbound_for_requester() throws Exception {
        AuthedUser expert = registerEnrolled();
        applyAsExpert(expert);
        AuthedUser owner = registerEnrolled();
        UUID groupId = createGroup(owner);
        createBookingRequest(owner, expert, groupId);

        mvc.perform(get("/api/expert-bookings/mine?asExpert=true").with(bearer(expert)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1));
        mvc.perform(get("/api/expert-bookings/mine").with(bearer(owner)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1));
        mvc.perform(get("/api/expert-bookings/mine?asExpert=true").with(bearer(owner)))
                .andExpect(jsonPath("$.data.length()").value(0));
    }

    @Test
    void create_with_inverted_time_range_returns_400() throws Exception {
        AuthedUser expert = registerEnrolled();
        applyAsExpert(expert);
        AuthedUser owner = registerEnrolled();
        UUID groupId = createGroup(owner);
        mvc.perform(post("/api/expert-bookings")
                        .with(bearer(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody(expert.id(), groupId,
                                Instant.now().plus(Duration.ofHours(2)),
                                Instant.now().plus(Duration.ofHours(1)),
                                "x")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_EVENT_TIME_RANGE"));
    }

    // --- helpers ---

    private void applyAsExpert(AuthedUser u) throws Exception {
        mvc.perform(post("/api/experts/apply")
                        .with(bearer(u))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"headline\":\"TA\"}"))
                .andExpect(status().isCreated());
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

    private UUID createBookingRequest(AuthedUser owner, AuthedUser expert, UUID groupId) throws Exception {
        Instant start = Instant.now().plus(Duration.ofDays(1));
        Instant end = start.plus(Duration.ofHours(1));
        String json = mvc.perform(post("/api/expert-bookings")
                        .with(bearer(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody(expert.id(), groupId, start, end, "help me")))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        JsonNode data = om.readTree(json).get("data");
        return UUID.fromString(data.get("id").asText());
    }

    private String requestBody(UUID expertId, UUID groupId, Instant start, Instant end, String message) {
        return String.format(
                "{\"expertUserId\":\"%s\",\"groupId\":\"%s\",\"proposedStartsAt\":\"%s\","
                        + "\"proposedEndsAt\":\"%s\",\"message\":\"%s\"}",
                expertId, groupId, start.toString(), end.toString(), message);
    }
}
