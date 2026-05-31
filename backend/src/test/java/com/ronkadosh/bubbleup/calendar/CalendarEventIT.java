package com.ronkadosh.bubbleup.calendar;

import com.ronkadosh.bubbleup.calendar.model.CalendarEvent;
import com.ronkadosh.bubbleup.calendar.model.CalendarEventType;
import com.ronkadosh.bubbleup.calendar.model.CalendarOwnerType;
import com.ronkadosh.bubbleup.calendar.persistence.CalendarEventRepository;
import com.ronkadosh.bubbleup.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class CalendarEventIT extends IntegrationTest {

    @Autowired CalendarEventRepository eventRepository;

    @Test
    void member_can_create_event() throws Exception {
        AuthedUser owner = registerAndLogin();
        UUID groupId = createGroup(owner);

        mvc.perform(post("/api/calendars/events")
                        .with(bearer(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createEventBody(groupId, "STUDY_SESSION", futurePlus(1), futurePlus(2))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.eventType").value("STUDY_SESSION"))
                .andExpect(jsonPath("$.data.ownerType").value("GROUP"))
                .andExpect(jsonPath("$.data.ownerId").value(groupId.toString()))
                .andExpect(jsonPath("$.data.createdBy").value(owner.id().toString()));
    }

    @Test
    void non_member_cannot_create_event() throws Exception {
        AuthedUser owner = registerAndLogin();
        AuthedUser outsider = registerAndLogin();
        UUID groupId = createGroup(owner);

        mvc.perform(post("/api/calendars/events")
                        .with(bearer(outsider))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createEventBody(groupId, "MEETING", futurePlus(1), futurePlus(2))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("NOT_GROUP_MEMBER"));
    }

    @Test
    void invalid_time_range_rejected() throws Exception {
        AuthedUser owner = registerAndLogin();
        UUID groupId = createGroup(owner);

        mvc.perform(post("/api/calendars/events")
                        .with(bearer(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createEventBody(groupId, "EXAM", futurePlus(2), futurePlus(1))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_EVENT_TIME_RANGE"));
    }

    @Test
    void list_returns_overlapping_events_in_range() throws Exception {
        AuthedUser owner = registerAndLogin();
        UUID groupId = createGroup(owner);
        createEvent(owner, groupId, "STUDY_SESSION", futurePlus(1), futurePlus(2));
        createEvent(owner, groupId, "EXAM", futurePlus(100), futurePlus(101));

        Instant from = futurePlus(0);
        Instant to = futurePlus(10);
        mvc.perform(get("/api/calendars/events")
                        .with(bearer(owner))
                        .param("ownerType", "GROUP")
                        .param("ownerId", groupId.toString())
                        .param("from", from.toString())
                        .param("to", to.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].eventType").value("STUDY_SESSION"));
    }

    @Test
    void second_overlapping_study_session_rejected() throws Exception {
        AuthedUser owner = registerAndLogin();
        UUID groupId = createGroup(owner);
        // First Live Bubble: [+1h, +2h].
        createEvent(owner, groupId, "STUDY_SESSION", futurePlus(1), futurePlus(2));

        // A second STUDY_SESSION overlapping the first must be rejected — otherwise
        // it spawns a "ghost" Live Bubble in the same window.
        mvc.perform(post("/api/calendars/events")
                        .with(bearer(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createEventBody(groupId, "STUDY_SESSION", futurePlus(1), futurePlus(2))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("GROUP_SCHEDULE_CONFLICT"));
    }

    @Test
    void non_overlapping_study_session_allowed() throws Exception {
        AuthedUser owner = registerAndLogin();
        UUID groupId = createGroup(owner);
        createEvent(owner, groupId, "STUDY_SESSION", futurePlus(1), futurePlus(2));

        // A later, non-overlapping STUDY_SESSION is fine — the rule is "one at a
        // time", not "one ever".
        mvc.perform(post("/api/calendars/events")
                        .with(bearer(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createEventBody(groupId, "STUDY_SESSION", futurePlus(3), futurePlus(4))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.eventType").value("STUDY_SESSION"));
    }

    @Test
    void create_event_in_past_rejected() throws Exception {
        AuthedUser owner = registerAndLogin();
        UUID groupId = createGroup(owner);

        // The calendar is fixated: you can't schedule an event whose start is
        // already behind "now".
        mvc.perform(post("/api/calendars/events")
                        .with(bearer(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createEventBody(groupId, "MEETING", futurePlus(-2), futurePlus(-1))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("EVENT_STARTS_IN_PAST"));
    }

    @Test
    void update_already_started_event_rejected() throws Exception {
        AuthedUser owner = registerAndLogin();
        UUID groupId = createGroup(owner);
        // Seed a past event directly (the create endpoint refuses past starts).
        UUID eventId = eventRepository.save(CalendarEvent.builder()
                .ownerType(CalendarOwnerType.GROUP)
                .ownerId(groupId)
                .createdBy(owner.id())
                .eventType(CalendarEventType.MEETING)
                .startsAt(futurePlus(-2))
                .endsAt(futurePlus(-1))
                .build()).getId();

        // Once its start has passed the event is fixated — even its author can't edit it.
        mvc.perform(patch("/api/calendars/events/{id}", eventId)
                        .with(bearer(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"description\":\"too late\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("EVENT_ALREADY_STARTED"));
    }

    @Test
    void delete_already_started_event_rejected() throws Exception {
        AuthedUser owner = registerAndLogin();
        UUID groupId = createGroup(owner);
        UUID eventId = eventRepository.save(CalendarEvent.builder()
                .ownerType(CalendarOwnerType.GROUP)
                .ownerId(groupId)
                .createdBy(owner.id())
                .eventType(CalendarEventType.MEETING)
                .startsAt(futurePlus(-2))
                .endsAt(futurePlus(-1))
                .build()).getId();

        // A fixated (already-started) event can't be deleted either.
        mvc.perform(delete("/api/calendars/events/{id}", eventId).with(bearer(owner)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("EVENT_ALREADY_STARTED"));
    }

    @Test
    void update_own_event_happy() throws Exception {
        AuthedUser owner = registerAndLogin();
        UUID groupId = createGroup(owner);
        UUID eventId = createEvent(owner, groupId, "STUDY_SESSION", futurePlus(1), futurePlus(2));

        mvc.perform(patch("/api/calendars/events/{id}", eventId)
                        .with(bearer(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"eventType\":\"REMINDER\",\"description\":\"updated\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.eventType").value("REMINDER"))
                .andExpect(jsonPath("$.data.description").value("updated"));
    }

    @Test
    void group_owner_can_update_other_members_event() throws Exception {
        AuthedUser owner = registerAndLogin();
        AuthedUser member = registerAndLogin();
        UUID groupId = createGroup(owner);
        joinGroup(member, groupId);
        UUID eventId = createEvent(member, groupId, "MEETING", futurePlus(1), futurePlus(2));

        mvc.perform(patch("/api/calendars/events/{id}", eventId)
                        .with(bearer(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"description\":\"owner edit\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.description").value("owner edit"));
    }

    @Test
    void non_owner_non_author_cannot_update() throws Exception {
        AuthedUser owner = registerAndLogin();
        AuthedUser m1 = registerAndLogin();
        AuthedUser m2 = registerAndLogin();
        UUID groupId = createGroup(owner);
        joinGroup(m1, groupId);
        joinGroup(m2, groupId);
        UUID eventId = createEvent(m1, groupId, "STUDY_SESSION", futurePlus(1), futurePlus(2));

        mvc.perform(patch("/api/calendars/events/{id}", eventId)
                        .with(bearer(m2))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"description\":\"hijacked\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("NOT_EVENT_AUTHOR_OR_OWNER"));
    }

    @Test
    void author_can_delete_own_event() throws Exception {
        AuthedUser owner = registerAndLogin();
        UUID groupId = createGroup(owner);
        UUID eventId = createEvent(owner, groupId, "STUDY_SESSION", futurePlus(1), futurePlus(2));

        mvc.perform(delete("/api/calendars/events/{id}", eventId).with(bearer(owner)))
                .andExpect(status().isOk());
        mvc.perform(get("/api/calendars/events/{id}", eventId).with(bearer(owner)))
                .andExpect(status().isNotFound());
    }

    @Test
    void deleting_group_cascades_events() throws Exception {
        AuthedUser owner = registerAndLogin();
        UUID groupId = createGroup(owner);
        UUID eventId = createEvent(owner, groupId, "EXAM", futurePlus(1), futurePlus(2));

        mvc.perform(delete("/api/groups/{id}", groupId).with(bearer(owner)))
                .andExpect(status().isOk());
        assertTrue(eventRepository.findById(eventId).isEmpty());
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

    private void joinGroup(AuthedUser u, UUID groupId) throws Exception {
        mvc.perform(post("/api/groups/{id}/join", groupId).with(bearer(u)))
                .andExpect(status().isOk());
    }

    private UUID createEvent(AuthedUser u, UUID groupId, String type, Instant from, Instant to) throws Exception {
        String json = mvc.perform(post("/api/calendars/events")
                        .with(bearer(u))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createEventBody(groupId, type, from, to)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return UUID.fromString(om.readTree(json).get("data").get("id").asText());
    }

    private static String createEventBody(UUID groupId, String type, Instant from, Instant to) {
        return String.format(
                "{\"ownerType\":\"GROUP\",\"ownerId\":\"%s\",\"eventType\":\"%s\",\"startsAt\":\"%s\",\"endsAt\":\"%s\"}",
                groupId, type, from, to);
    }

    private static Instant futurePlus(long hours) {
        return Instant.now().plusSeconds(hours * 3600);
    }
}
