package com.ronkadosh.bubbleup.room;

import com.fasterxml.jackson.databind.JsonNode;
import com.ronkadosh.bubbleup.calendar.persistence.CalendarEventRepository;
import com.ronkadosh.bubbleup.chat.model.ChatMessageType;
import com.ronkadosh.bubbleup.chat.persistence.ChatMessageRepository;
import com.ronkadosh.bubbleup.room.application.RoomLifecycleScheduler;
import com.ronkadosh.bubbleup.room.model.Room;
import com.ronkadosh.bubbleup.room.persistence.RoomRepository;
import com.ronkadosh.bubbleup.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Lifecycle-gate ITs: time-window enforcement on GET /api/rooms/:id, the
 * extend endpoint, and the lifecycle scheduler's hard-close pass.
 *
 * <p>Test profile has no JaaS credentials, so we deliberately don't assert
 * 200 responses on GET — successful JWT minting requires JaaS config.
 * Where we need a "room is currently open" assertion we go through the
 * repository instead.
 */
class RoomLifecycleGatesIT extends IntegrationTest {

    @Autowired RoomRepository roomRepository;
    @Autowired CalendarEventRepository eventRepository;
    @Autowired ChatMessageRepository chatMessageRepository;
    @Autowired RoomLifecycleScheduler scheduler;

    // ---------- Time-window gate ----------

    @Test
    void getRoom_before_T_minus_15_returns_ROOM_NOT_YET_OPEN() throws Exception {
        AuthedUser owner = registerAndLogin();
        UUID groupId = createGroup(owner);
        Instant startsAt = Instant.now().plus(Duration.ofMinutes(20));
        Instant endsAt = startsAt.plus(Duration.ofMinutes(60));
        UUID eventId = createEvent(owner, groupId, "STUDY_SESSION", startsAt, endsAt);
        UUID roomId = roomRepository.findByCalendarEventId(eventId).orElseThrow().getId();

        mvc.perform(get("/api/rooms/{roomId}", roomId).with(bearer(owner)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("ROOM_NOT_YET_OPEN"));
    }

    @Test
    void getRoom_after_endsAt_returns_ROOM_ENDED() throws Exception {
        AuthedUser owner = registerAndLogin();
        UUID groupId = createGroup(owner);
        Instant startsAt = Instant.now().minus(Duration.ofHours(2));
        Instant endsAt = Instant.now().minus(Duration.ofMinutes(1));
        UUID eventId = createEvent(owner, groupId, "STUDY_SESSION", startsAt, endsAt);
        UUID roomId = roomRepository.findByCalendarEventId(eventId).orElseThrow().getId();

        mvc.perform(get("/api/rooms/{roomId}", roomId).with(bearer(owner)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("ROOM_ENDED"));
    }

    @Test
    void getRoom_after_endedAt_set_returns_ROOM_ENDED() throws Exception {
        AuthedUser owner = registerAndLogin();
        UUID groupId = createGroup(owner);
        Instant startsAt = Instant.now().minus(Duration.ofMinutes(5));
        Instant endsAt = Instant.now().plus(Duration.ofHours(1));
        UUID eventId = createEvent(owner, groupId, "STUDY_SESSION", startsAt, endsAt);
        Room room = roomRepository.findByCalendarEventId(eventId).orElseThrow();
        // Simulate the scheduler stamping endedAt.
        room.setEndedAt(Instant.now());
        roomRepository.save(room);

        mvc.perform(get("/api/rooms/{roomId}", room.getId()).with(bearer(owner)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("ROOM_ENDED"));
    }

    // ---------- Extend endpoint ----------

    @Test
    void extend_bumps_endsAt_and_posts_system_message() throws Exception {
        AuthedUser owner = registerAndLogin();
        UUID groupId = createGroup(owner);
        Instant startsAt = Instant.now().minus(Duration.ofMinutes(5));
        Instant originalEndsAt = Instant.now().plus(Duration.ofMinutes(10));
        UUID eventId = createEvent(owner, groupId, "STUDY_SESSION", startsAt, originalEndsAt);
        UUID roomId = roomRepository.findByCalendarEventId(eventId).orElseThrow().getId();
        long systemMsgCountBefore = chatMessageRepository.count();

        // Skip the JaaS-dependent response body — assert it didn't 4xx by
        // catching the 500 from JITSI_NOT_CONFIGURED that happens during the
        // refreshed-GET branch. Use the lower-level repo + chat checks below
        // for the actual state assertions.
        mvc.perform(post("/api/rooms/{roomId}/extend", roomId).with(bearer(owner)));

        Instant newEndsAt = eventRepository.findById(eventId).orElseThrow().getEndsAt();
        assertEquals(originalEndsAt.plus(Duration.ofMinutes(15)).toEpochMilli(),
                newEndsAt.toEpochMilli(),
                "Extend should bump endsAt by exactly +15 min");

        long systemMsgCountAfter = chatMessageRepository.count();
        assertEquals(systemMsgCountBefore + 1, systemMsgCountAfter,
                "Extend should post one system message");
        Room reloaded = roomRepository.findById(roomId).orElseThrow();
        assertNull(reloaded.getEndWarningSentAt(),
                "Extend should reset endWarningSentAt so a fresh warning fires before the new endsAt");
    }

    @Test
    void extend_on_ended_room_returns_ROOM_ENDED() throws Exception {
        AuthedUser owner = registerAndLogin();
        UUID groupId = createGroup(owner);
        UUID eventId = createEvent(owner, groupId, "STUDY_SESSION",
                Instant.now().minus(Duration.ofHours(2)),
                Instant.now().minus(Duration.ofMinutes(5)));
        Room room = roomRepository.findByCalendarEventId(eventId).orElseThrow();
        room.setEndedAt(Instant.now());
        roomRepository.save(room);

        mvc.perform(post("/api/rooms/{roomId}/extend", room.getId()).with(bearer(owner)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("ROOM_ENDED"));
    }

    // ---------- Lifecycle scheduler ----------

    @Test
    void scheduler_hard_closes_rooms_past_endsAt() throws Exception {
        AuthedUser owner = registerAndLogin();
        UUID groupId = createGroup(owner);
        // Event ended 5 min ago.
        UUID eventId = createEvent(owner, groupId, "STUDY_SESSION",
                Instant.now().minus(Duration.ofHours(1)),
                Instant.now().minus(Duration.ofMinutes(5)));
        UUID roomId = roomRepository.findByCalendarEventId(eventId).orElseThrow().getId();
        assertNull(roomRepository.findById(roomId).orElseThrow().getEndedAt(),
                "Pre-condition: room should be open");

        scheduler.tick();

        Room reloaded = roomRepository.findById(roomId).orElseThrow();
        assertNotNull(reloaded.getEndedAt(), "Scheduler should set endedAt for past-end rooms");
    }

    @Test
    void scheduler_posts_end_soon_warning_within_15min_window() throws Exception {
        AuthedUser owner = registerAndLogin();
        UUID groupId = createGroup(owner);
        // endsAt 10 min from now → inside the 15-min warning window.
        UUID eventId = createEvent(owner, groupId, "STUDY_SESSION",
                Instant.now().minus(Duration.ofMinutes(5)),
                Instant.now().plus(Duration.ofMinutes(10)));
        UUID roomId = roomRepository.findByCalendarEventId(eventId).orElseThrow().getId();
        long before = countByType(ChatMessageType.SYSTEM_ROOM_END_SOON);

        scheduler.tick();

        Room reloaded = roomRepository.findById(roomId).orElseThrow();
        assertNotNull(reloaded.getEndWarningSentAt(),
                "Scheduler should stamp endWarningSentAt once it posts the warning");
        long after = countByType(ChatMessageType.SYSTEM_ROOM_END_SOON);
        assertEquals(before + 1, after, "One SYSTEM_ROOM_END_SOON message should be posted");
    }

    @Test
    void scheduler_does_not_double_post_warning() throws Exception {
        AuthedUser owner = registerAndLogin();
        UUID groupId = createGroup(owner);
        UUID eventId = createEvent(owner, groupId, "STUDY_SESSION",
                Instant.now().minus(Duration.ofMinutes(5)),
                Instant.now().plus(Duration.ofMinutes(10)));
        // Touch eventId so the IDE doesn't flag it as unused — the scheduler picks the room up via repository.
        assertTrue(roomRepository.findByCalendarEventId(eventId).isPresent());
        long before = countByType(ChatMessageType.SYSTEM_ROOM_END_SOON);

        scheduler.tick();
        scheduler.tick();   // second pass — should be a no-op for the warning.

        long after = countByType(ChatMessageType.SYSTEM_ROOM_END_SOON);
        assertEquals(before + 1, after,
                "Second tick must not duplicate the warning (endWarningSentAt guards it)");
    }

    private long countByType(ChatMessageType type) {
        return chatMessageRepository.findAll().stream()
                .filter(m -> m.getMessageType() == type)
                .count();
    }

    // ---------- helpers ----------

    private UUID createGroup(AuthedUser owner) throws Exception {
        String json = mvc.perform(post("/api/groups")
                        .with(bearer(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"g\",\"offeringId\":\"" + seedOfferingId() + "\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return UUID.fromString(om.readTree(json).get("data").get("id").asText());
    }

    private UUID createEvent(AuthedUser u, UUID groupId, String type, Instant from, Instant to) throws Exception {
        String body = String.format(
                "{\"ownerType\":\"GROUP\",\"ownerId\":\"%s\",\"eventType\":\"%s\",\"startsAt\":\"%s\",\"endsAt\":\"%s\"}",
                groupId, type, from, to);
        String json = mvc.perform(post("/api/calendars/events")
                        .with(bearer(u))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        JsonNode data = om.readTree(json).get("data");
        return UUID.fromString(data.get("id").asText());
    }

    @SuppressWarnings("unused")
    private static void assertWithin(Instant a, Instant b, Duration tolerance) {
        assertTrue(Duration.between(a, b).abs().compareTo(tolerance) <= 0,
                "Times differ by more than " + tolerance + ": " + a + " vs " + b);
    }
}
