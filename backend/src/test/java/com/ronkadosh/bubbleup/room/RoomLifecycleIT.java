package com.ronkadosh.bubbleup.room;

import com.ronkadosh.bubbleup.calendar.persistence.CalendarEventRepository;
import com.ronkadosh.bubbleup.room.persistence.RoomRepository;
import com.ronkadosh.bubbleup.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Verifies that the room lifecycle is correctly driven by STUDY_SESSION calendar events.
 *
 * <p>Test profile has no JaaS credentials, so we don't exercise the happy-path GET /api/rooms/{id}
 * (which would synthesize a JWT) — those checks belong in manual verification. Here we focus on
 * the create/update/delete state transitions visible via the repository.
 */
class RoomLifecycleIT extends IntegrationTest {

    @Autowired RoomRepository roomRepository;
    @Autowired CalendarEventRepository eventRepository;

    @Test
    void study_session_event_creates_room() throws Exception {
        AuthedUser owner = registerEnrolled();
        UUID groupId = createGroup(owner);

        UUID eventId = createEvent(owner, groupId, "STUDY_SESSION", futurePlus(1), futurePlus(2));

        assertTrue(roomRepository.existsByCalendarEventId(eventId),
                "Creating a STUDY_SESSION event should auto-create a Room");
        var room = roomRepository.findByCalendarEventId(eventId).orElseThrow();
        assertTrue(room.getGroupId().equals(groupId));
        assertTrue(room.getJitsiRoomName() != null && !room.getJitsiRoomName().isBlank());
        assertTrue(room.getChatRoomId() != null, "Room must point at the bubble's general chat");
    }

    @Test
    void non_study_session_event_does_not_create_room() throws Exception {
        AuthedUser owner = registerEnrolled();
        UUID groupId = createGroup(owner);

        UUID eventId = createEvent(owner, groupId, "MEETING", futurePlus(1), futurePlus(2));

        assertFalse(roomRepository.existsByCalendarEventId(eventId),
                "Non-STUDY_SESSION events should not spawn a Room");
    }

    @Test
    void deleting_event_deletes_room() throws Exception {
        AuthedUser owner = registerEnrolled();
        UUID groupId = createGroup(owner);
        UUID eventId = createEvent(owner, groupId, "STUDY_SESSION", futurePlus(1), futurePlus(2));
        assertTrue(roomRepository.existsByCalendarEventId(eventId));

        mvc.perform(delete("/api/calendars/events/{id}", eventId).with(bearer(owner)))
                .andExpect(status().isOk());

        assertFalse(roomRepository.existsByCalendarEventId(eventId),
                "Deleting the event should cascade-delete the Room");
    }

    @Test
    void updating_event_from_study_session_deletes_room() throws Exception {
        AuthedUser owner = registerEnrolled();
        UUID groupId = createGroup(owner);
        UUID eventId = createEvent(owner, groupId, "STUDY_SESSION", futurePlus(1), futurePlus(2));
        assertTrue(roomRepository.existsByCalendarEventId(eventId));

        mvc.perform(patch("/api/calendars/events/{id}", eventId)
                        .with(bearer(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"eventType\":\"MEETING\"}"))
                .andExpect(status().isOk());

        assertFalse(roomRepository.existsByCalendarEventId(eventId),
                "Flipping eventType away from STUDY_SESSION should drop the Room");
    }

    @Test
    void updating_event_to_study_session_creates_room() throws Exception {
        AuthedUser owner = registerEnrolled();
        UUID groupId = createGroup(owner);
        UUID eventId = createEvent(owner, groupId, "MEETING", futurePlus(1), futurePlus(2));
        assertFalse(roomRepository.existsByCalendarEventId(eventId));

        mvc.perform(patch("/api/calendars/events/{id}", eventId)
                        .with(bearer(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"eventType\":\"STUDY_SESSION\"}"))
                .andExpect(status().isOk());

        assertTrue(roomRepository.existsByCalendarEventId(eventId),
                "Flipping eventType to STUDY_SESSION should spawn a Room");
    }

    @Test
    void deleting_group_cascades_rooms() throws Exception {
        AuthedUser owner = registerEnrolled();
        UUID groupId = createGroup(owner);
        UUID eventId = createEvent(owner, groupId, "STUDY_SESSION", futurePlus(1), futurePlus(2));
        UUID roomId = roomRepository.findByCalendarEventId(eventId).orElseThrow().getId();

        mvc.perform(delete("/api/groups/{id}", groupId).with(bearer(owner)))
                .andExpect(status().isOk());

        assertFalse(roomRepository.existsById(roomId),
                "Deleting the bubble must cascade through events to delete rooms");
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
        return UUID.fromString(om.readTree(json).get("data").get("id").asText());
    }

    private static Instant futurePlus(long hours) {
        return Instant.now().plusSeconds(hours * 3600);
    }
}
