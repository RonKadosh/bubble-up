package com.ronkadosh.bubbleup.e2e;

import com.fasterxml.jackson.databind.JsonNode;
import com.ronkadosh.bubbleup.support.IntegrationTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Base class for end-to-end <em>journey</em> tests.
 *
 * <p>Where the per-feature {@code *IT} classes assert one endpoint at a time, the
 * {@code *JourneyE2EIT} classes that extend this one compose the exact HTTP call
 * sequences the frontend makes (see {@code frontend/src/api/*.ts}) into whole user
 * stories from {@code FLOWS.md}. The point is to prove that the screens' real
 * call-chains hang together, not just that each handler works in isolation.
 *
 * <p>The helpers here mirror the frontend {@code api/} functions 1:1 — most
 * importantly {@link #createBubble} POSTs {@code courseId} (what
 * {@code GroupSidebar}/{@code GroupsPage} send), not {@code offeringId}, so these
 * tests drive the same wire contract a browser does.
 *
 * <p>Note on the shared H2 schema: the test DB is in-memory and lives for the whole
 * run, so rows from sibling tests accumulate. Global-list assertions therefore use
 * {@code exists()}-style JSON-path filters on unique names, never fixed lengths —
 * per-user / per-entity reads (own bubbles, own rooms) are scoped and safe to count.
 */
public abstract class E2EFlowTest extends IntegrationTest {

    // ------------------------------------------------------------------ generic

    protected JsonNode dataOf(ResultActions actions) throws Exception {
        String json = actions.andReturn().getResponse().getContentAsString();
        return om.readTree(json).get("data");
    }

    /** GET {@code path} as {@code u}, expect 200, return the unwrapped {@code data} node. */
    protected JsonNode getData(AuthedUser u, String path) throws Exception {
        return dataOf(mvc.perform(get(path).with(bearer(u))).andExpect(status().isOk()));
    }

    // -------------------------------------------------------- groups (api/groups.ts)

    /**
     * POST /api/groups exactly as the create-bubble form does — with {@code courseId}
     * (resolved server-side to the current-term offering). The caller must already be
     * enrolled in that course; use {@link #registerEnrolled()} for the owner.
     */
    protected UUID createBubble(AuthedUser owner, String name, String visibility) throws Exception {
        JsonNode data = dataOf(mvc.perform(post("/api/groups")
                        .with(bearer(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(String.format(
                                "{\"name\":\"%s\",\"description\":\"\",\"visibility\":\"%s\",\"maxMembers\":6,\"courseId\":\"%s\"}",
                                name, visibility, seedOfferingCourseId())))
                .andExpect(status().isCreated()));
        return UUID.fromString(data.get("id").asText());
    }

    protected UUID createPublicBubble(AuthedUser owner, String name) throws Exception {
        return createBubble(owner, name, "PUBLIC");
    }

    protected void joinBubble(AuthedUser u, UUID groupId) throws Exception {
        mvc.perform(post("/api/groups/{id}/join", groupId).with(bearer(u)))
                .andExpect(status().isOk());
    }

    // ----------------------------------------------------------- chat (api/chat.ts)

    /** The bubble's auto-created "general" room — what {@code ChatPanel} picks (oldest room). */
    protected UUID defaultRoomId(AuthedUser member) throws Exception {
        return UUID.fromString(getData(member, "/api/chat/rooms").get(0).get("id").asText());
    }

    protected UUID sendText(AuthedUser sender, UUID roomId, String content) throws Exception {
        JsonNode data = dataOf(mvc.perform(post("/api/chat/rooms/{id}/messages", roomId)
                        .with(bearer(sender))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(String.format("{\"type\":\"TEXT\",\"content\":\"%s\"}", content)))
                .andExpect(status().isCreated()));
        // sentAt is Instant.now() (ms precision on Windows); pace so strictly-greater
        // unread comparisons stay deterministic — same trick as LastReadIT.
        Thread.sleep(2);
        return UUID.fromString(data.get("id").asText());
    }

    protected void markRead(AuthedUser u, UUID roomId, UUID lastReadMessageId) throws Exception {
        mvc.perform(post("/api/chat/rooms/{id}/read", roomId)
                        .with(bearer(u))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(String.format("{\"lastReadMessageId\":\"%s\"}", lastReadMessageId)))
                .andExpect(status().isOk());
    }

    // ------------------------------------------------------- calendar (api/calendar.ts)

    protected UUID createGroupEvent(AuthedUser u, UUID groupId, String type, Instant start, Instant end) throws Exception {
        JsonNode data = dataOf(mvc.perform(post("/api/calendars/events")
                        .with(bearer(u))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(String.format(
                                "{\"ownerType\":\"GROUP\",\"ownerId\":\"%s\",\"eventType\":\"%s\",\"startsAt\":\"%s\",\"endsAt\":\"%s\"}",
                                groupId, type, start, end)))
                .andExpect(status().isCreated()));
        return UUID.fromString(data.get("id").asText());
    }

    // --------------------------------------------------------- expert (api/expert.ts)

    /** POST /api/experts/apply — v1 auto-verifies and promotes the user to EXPERT. */
    protected void applyAsExpert(AuthedUser u, String headline) throws Exception {
        mvc.perform(post("/api/experts/apply")
                        .with(bearer(u))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(String.format("{\"headline\":\"%s\"}", headline)))
                .andExpect(status().isCreated());
    }

    protected UUID createExpertSession(AuthedUser expert, String title, int capacity, Instant start, Instant end) throws Exception {
        JsonNode data = dataOf(mvc.perform(post("/api/expert-sessions")
                        .with(bearer(expert))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(String.format(
                                "{\"title\":\"%s\",\"startsAt\":\"%s\",\"endsAt\":\"%s\",\"capacity\":%d}",
                                title, start, end, capacity)))
                .andExpect(status().isCreated()));
        return UUID.fromString(data.get("id").asText());
    }

    protected void enrollGroupInSession(AuthedUser owner, UUID sessionId, UUID groupId) throws Exception {
        mvc.perform(post("/api/expert-sessions/{id}/enroll", sessionId)
                        .with(bearer(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"groupId\":\"" + groupId + "\"}"))
                .andExpect(status().isCreated());
    }

    // ------------------------------------------------------------------ time

    protected static Instant hoursFromNow(long h) {
        return Instant.now().plus(Duration.ofHours(h));
    }

    protected static Instant minutesFromNow(long m) {
        return Instant.now().plus(Duration.ofMinutes(m));
    }

    /** A short unique suffix so global-list assertions never collide across tests. */
    protected static String unique() {
        return UUID.randomUUID().toString().substring(0, 8);
    }
}
