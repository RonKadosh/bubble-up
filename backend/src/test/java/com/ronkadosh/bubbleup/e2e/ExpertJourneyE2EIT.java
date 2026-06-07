package com.ronkadosh.bubbleup.e2e;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * FLOWS §11 (Become an expert → dashboard → directory → enroll a group → booking)
 * and §10 (host whiteboard grant/revoke + participants).
 */
class ExpertJourneyE2EIT extends E2EFlowTest {

    @Test
    void become_expert_then_appear_in_directory_with_an_open_session() throws Exception {
        AuthedUser expert = registerEnrolled();

        // /become-expert form → auto-verified + role promoted.
        applyAsExpert(expert, "Linear Algebra TA " + unique());
        mvc.perform(get("/api/experts/me").with(bearer(expert)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.verificationStatus").value("VERIFIED"));

        // Expert dashboard "Schedule" → a session (+ its room) is created OPEN.
        UUID sessionId = createExpertSession(expert, "Q&A " + unique(), 3, hoursFromNow(1), hoursFromNow(2));
        mvc.perform(get("/api/expert-sessions/mine").with(bearer(expert)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[?(@.id == '" + sessionId + "')]").exists());

        // Directory + open-sessions browse list them.
        mvc.perform(get("/api/experts/directory").with(bearer(expert)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[?(@.userId == '" + expert.id() + "')]").exists());
        mvc.perform(get("/api/expert-sessions/open").with(bearer(expert)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[?(@.id == '" + sessionId + "')]").exists());
    }

    @Test
    void group_owner_enrolls_a_group_and_session_surfaces_on_its_calendar() throws Exception {
        AuthedUser expert = registerEnrolled();
        applyAsExpert(expert, "Tutor " + unique());
        UUID sessionId = createExpertSession(expert, "Tutoring " + unique(), 3, hoursFromNow(1), hoursFromNow(2));

        AuthedUser owner = registerEnrolled();
        UUID groupId = createPublicBubble(owner, "Enrolling " + unique());

        // Directory "Enroll" → group joins the session, count goes up.
        enrollGroupInSession(owner, sessionId, groupId);
        mvc.perform(get("/api/expert-sessions/{id}", sessionId).with(bearer(expert)))
                .andExpect(jsonPath("$.data.enrolledGroupCount").value(1));

        // The session now shows on the group's calendar as an EXPERT_SESSION event
        // (the backend merges enrolled sessions into the group calendar query).
        String from = java.time.Instant.now().minusSeconds(86400).toString();
        String to = hoursFromNow(168).toString();
        mvc.perform(get("/api/calendars/events")
                        .param("ownerType", "GROUP").param("ownerId", groupId.toString())
                        .param("from", from).param("to", to).with(bearer(owner)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[?(@.eventType == 'EXPERT_SESSION')]").exists());

        // Also reachable via the group-scoped enrolled-sessions endpoint.
        mvc.perform(get("/api/expert-sessions/enrolled").param("groupId", groupId.toString())
                        .with(bearer(owner)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[?(@.id == '" + sessionId + "')]").exists());
    }

    @Test
    void booking_request_lifecycle_send_accept_and_withdraw() throws Exception {
        AuthedUser expert = registerEnrolled();
        applyAsExpert(expert, "Mentor " + unique());

        AuthedUser owner = registerEnrolled();
        UUID groupId = createPublicBubble(owner, "Booking " + unique());

        // RequestBookingModal → POST /api/expert-bookings.
        UUID requestId = UUID.fromString(dataOf(mvc.perform(post("/api/expert-bookings")
                        .with(bearer(owner)).contentType(MediaType.APPLICATION_JSON)
                        .content(String.format(
                                "{\"expertUserId\":\"%s\",\"groupId\":\"%s\",\"proposedStartsAt\":\"%s\",\"proposedEndsAt\":\"%s\",\"message\":\"can you help?\"}",
                                expert.id(), groupId, hoursFromNow(3), hoursFromNow(4))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.status").value("PENDING")))
                .get("id").asText());

        // Expert inbox (asExpert=true) shows it → accept.
        mvc.perform(get("/api/expert-bookings/mine").param("asExpert", "true").with(bearer(expert)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[?(@.id == '" + requestId + "')]").exists());
        mvc.perform(post("/api/expert-bookings/{id}/accept", requestId).with(bearer(expert)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("ACCEPTED"));

        // A second request the owner withdraws from their outbox (asExpert=false).
        UUID toWithdraw = UUID.fromString(dataOf(mvc.perform(post("/api/expert-bookings")
                        .with(bearer(owner)).contentType(MediaType.APPLICATION_JSON)
                        .content(String.format(
                                "{\"expertUserId\":\"%s\",\"groupId\":\"%s\",\"proposedStartsAt\":\"%s\",\"proposedEndsAt\":\"%s\"}",
                                expert.id(), groupId, hoursFromNow(5), hoursFromNow(6))))
                .andExpect(status().isCreated())).get("id").asText());
        mvc.perform(post("/api/expert-bookings/{id}/withdraw", toWithdraw).with(bearer(owner)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("WITHDRAWN"));
    }

    @Test
    void host_grants_and_revokes_whiteboard_write_and_lists_participants() throws Exception {
        AuthedUser expert = registerEnrolled();
        applyAsExpert(expert, "Host " + unique());
        UUID sessionId = createExpertSession(expert, "Workshop " + unique(), 3, hoursFromNow(1), hoursFromNow(2));

        // Enroll a group so its members become authorized participants.
        AuthedUser owner = registerEnrolled();
        UUID groupId = createPublicBubble(owner, "Participants " + unique());
        enrollGroupInSession(owner, sessionId, groupId);

        // Host opens the participants panel.
        mvc.perform(get("/api/expert-sessions/{id}/participants", sessionId).with(bearer(expert)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[?(@.userId == '" + expert.id() + "')].isHost").value(true));

        // Grant then revoke whiteboard write for the group owner.
        mvc.perform(post("/api/expert-sessions/{id}/whiteboard/grant", sessionId)
                        .with(bearer(expert)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userId\":\"" + owner.id() + "\"}"))
                .andExpect(status().isOk());
        mvc.perform(post("/api/expert-sessions/{id}/whiteboard/revoke", sessionId)
                        .with(bearer(expert)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userId\":\"" + owner.id() + "\"}"))
                .andExpect(status().isOk());
    }

    @Test
    void non_expert_cannot_create_a_session() throws Exception {
        AuthedUser u = registerEnrolled();
        mvc.perform(post("/api/expert-sessions")
                        .with(bearer(u)).contentType(MediaType.APPLICATION_JSON)
                        .content(String.format(
                                "{\"title\":\"x\",\"startsAt\":\"%s\",\"endsAt\":\"%s\",\"capacity\":3}",
                                hoursFromNow(1), hoursFromNow(2))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("EXPERT_PROFILE_NOT_FOUND"));
    }

    @Test
    void host_cancels_a_session() throws Exception {
        AuthedUser expert = registerEnrolled();
        applyAsExpert(expert, "Canceller " + unique());
        UUID sessionId = createExpertSession(expert, "Cancelme " + unique(), 3, hoursFromNow(1), hoursFromNow(2));

        mvc.perform(post("/api/expert-sessions/{id}/cancel", sessionId).with(bearer(expert)))
                .andExpect(status().isOk());
        mvc.perform(get("/api/expert-sessions/{id}", sessionId).with(bearer(expert)))
                .andExpect(jsonPath("$.data.status").value("CANCELLED"));
    }
}
