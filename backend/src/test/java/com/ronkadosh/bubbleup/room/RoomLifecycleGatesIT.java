package com.ronkadosh.bubbleup.room;

import com.ronkadosh.bubbleup.calendar.model.CalendarEvent;
import com.ronkadosh.bubbleup.calendar.model.CalendarEventType;
import com.ronkadosh.bubbleup.calendar.model.CalendarOwnerType;
import com.ronkadosh.bubbleup.calendar.persistence.CalendarEventRepository;
import com.ronkadosh.bubbleup.chat.persistence.ChatMessageRepository;
import com.ronkadosh.bubbleup.room.application.RoomCallPresenceService;
import com.ronkadosh.bubbleup.room.application.RoomLifecycleScheduler;
import com.ronkadosh.bubbleup.room.internal.RoomInternalService;
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
    @Autowired RoomInternalService roomInternalService;
    @Autowired RoomCallPresenceService roomCallPresenceService;

    // ---------- Time-window gate ----------

    @Test
    void getRoom_before_T_minus_15_returns_ROOM_NOT_YET_OPEN() throws Exception {
        AuthedUser owner = registerEnrolled();
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
    void getRoom_after_endsAt_but_not_closed_is_still_joinable() throws Exception {
        // A room past its scheduled endsAt but NOT yet closed (endedAt null) stays
        // joinable — that's how an occupied room is kept alive into overtime. So the
        // join gate must NOT reject with ROOM_ENDED. In the test profile (no JaaS creds)
        // the request proceeds all the way to token issuance and fails there instead.
        AuthedUser owner = registerEnrolled();
        UUID groupId = createGroup(owner);
        Instant startsAt = Instant.now().minus(Duration.ofHours(2));
        Instant endsAt = Instant.now().minus(Duration.ofMinutes(1));
        UUID eventId = createEvent(owner, groupId, "STUDY_SESSION", startsAt, endsAt);
        UUID roomId = roomRepository.findByCalendarEventId(eventId).orElseThrow().getId();

        mvc.perform(get("/api/rooms/{roomId}", roomId).with(bearer(owner)))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.error.code").value("JITSI_NOT_CONFIGURED"));
    }

    @Test
    void getRoom_after_endedAt_set_returns_ROOM_ENDED() throws Exception {
        AuthedUser owner = registerEnrolled();
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
        AuthedUser owner = registerEnrolled();
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
        AuthedUser owner = registerEnrolled();
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
    void scheduler_closes_empty_room_past_endsAt_grace() throws Exception {
        AuthedUser owner = registerEnrolled();
        UUID groupId = createGroup(owner);
        // Event ended 5 min ago and nobody is in the call → past the empty grace → close.
        UUID eventId = createEvent(owner, groupId, "STUDY_SESSION",
                Instant.now().minus(Duration.ofHours(1)),
                Instant.now().minus(Duration.ofMinutes(5)));
        UUID roomId = roomRepository.findByCalendarEventId(eventId).orElseThrow().getId();
        assertNull(roomRepository.findById(roomId).orElseThrow().getEndedAt(),
                "Pre-condition: room should be open");

        scheduler.tick();

        Room reloaded = roomRepository.findById(roomId).orElseThrow();
        assertNotNull(reloaded.getEndedAt(),
                "An empty room well past endsAt (beyond the grace) should be hard-closed");
    }

    @Test
    void scheduler_keeps_occupied_room_open_past_endsAt() throws Exception {
        AuthedUser owner = registerEnrolled();
        UUID groupId = createGroup(owner);
        // Ended 5 min ago, but someone is still in the call → stay alive.
        UUID eventId = createEvent(owner, groupId, "STUDY_SESSION",
                Instant.now().minus(Duration.ofHours(1)),
                Instant.now().minus(Duration.ofMinutes(5)));
        UUID roomId = roomRepository.findByCalendarEventId(eventId).orElseThrow().getId();
        roomCallPresenceService.join(roomId, owner.id());

        scheduler.tick();

        assertNull(roomRepository.findById(roomId).orElseThrow().getEndedAt(),
                "An occupied room must not be closed even past endsAt");
    }

    @Test
    void scheduler_keeps_empty_room_within_grace() throws Exception {
        AuthedUser owner = registerEnrolled();
        UUID groupId = createGroup(owner);
        // Ended only 30s ago — within the 2-min empty grace, even though never occupied.
        UUID eventId = createEvent(owner, groupId, "STUDY_SESSION",
                Instant.now().minus(Duration.ofMinutes(30)),
                Instant.now().minus(Duration.ofSeconds(30)));
        UUID roomId = roomRepository.findByCalendarEventId(eventId).orElseThrow().getId();

        scheduler.tick();

        assertNull(roomRepository.findById(roomId).orElseThrow().getEndedAt(),
                "A room just past endsAt (within the empty grace) should not be closed yet");
    }

    @Test
    void scheduler_keeps_room_within_grace_after_last_leave() throws Exception {
        AuthedUser owner = registerEnrolled();
        UUID groupId = createGroup(owner);
        // Ended 5 min ago, but the last occupant only just left → grace runs from the leave.
        UUID eventId = createEvent(owner, groupId, "STUDY_SESSION",
                Instant.now().minus(Duration.ofHours(1)),
                Instant.now().minus(Duration.ofMinutes(5)));
        UUID roomId = roomRepository.findByCalendarEventId(eventId).orElseThrow().getId();
        roomCallPresenceService.join(roomId, owner.id());
        roomCallPresenceService.leave(roomId, owner.id());   // stamps lastOccupiedAt ≈ now

        scheduler.tick();

        assertNull(roomRepository.findById(roomId).orElseThrow().getEndedAt(),
                "Room should stay open during the grace window measured from the last leave");
    }

    // ---------- helpers ----------

    private UUID createGroup(AuthedUser owner) throws Exception {
        String json = mvc.perform(post("/api/groups")
                        .with(bearer(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"g\",\"maxMembers\":6,\"offeringId\":\"" + seedOfferingId() + "\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return UUID.fromString(om.readTree(json).get("data").get("id").asText());
    }

    /**
     * Seeds a STUDY_SESSION event + its room directly via the repository / room
     * internal service, then returns the event id. We can't use the create
     * endpoint here: it now rejects past starts (EVENT_STARTS_IN_PAST), and these
     * lifecycle gates need rooms whose event window is already in progress or
     * past — which in production arise from an event created earlier whose time
     * has since passed, exactly what seeding-via-repo models.
     */
    private UUID createEvent(AuthedUser u, UUID groupId, String type, Instant from, Instant to) {
        UUID eventId = eventRepository.save(CalendarEvent.builder()
                .ownerType(CalendarOwnerType.GROUP)
                .ownerId(groupId)
                .createdBy(u.id())
                .eventType(CalendarEventType.valueOf(type))
                .startsAt(from)
                .endsAt(to)
                .build()).getId();
        roomInternalService.createRoomForCalendarEvent(eventId, u.id());
        return eventId;
    }

    @SuppressWarnings("unused")
    private static void assertWithin(Instant a, Instant b, Duration tolerance) {
        assertTrue(Duration.between(a, b).abs().compareTo(tolerance) <= 0,
                "Times differ by more than " + tolerance + ": " + a + " vs " + b);
    }
}
