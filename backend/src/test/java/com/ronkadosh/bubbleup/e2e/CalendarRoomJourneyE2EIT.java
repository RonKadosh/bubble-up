package com.ronkadosh.bubbleup.e2e;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * FLOWS §7 (Calendar panel: create / edit / delete on the month grid), §5.8–5.9
 * (Schedule a live room → Join live), and the §9 room-entry time gate.
 *
 * <p>Jitsi isn't configured in the test profile, so the happy-path room fetch
 * (which mints a JWT) is out of scope here — we drive the pre-open gate
 * ({@code ROOM_NOT_YET_OPEN}), which is exactly the "Opens in N min" disabled
 * state the calendar card and header render before the window opens.
 */
class CalendarRoomJourneyE2EIT extends E2EFlowTest {

    @Test
    void create_edit_and_delete_a_group_event() throws Exception {
        AuthedUser owner = registerEnrolled();
        UUID groupId = createPublicBubble(owner, "Cal " + unique());

        // "New event" on an empty day → MEETING.
        UUID eventId = createGroupEvent(owner, groupId, "MEETING", hoursFromNow(24), hoursFromNow(25));

        // Agenda / month-grid load → GET /api/calendars/events over the window.
        String from = Instant.now().minusSeconds(3600).toString();
        String to = hoursFromNow(48).toString();
        mvc.perform(get("/api/calendars/events")
                        .param("ownerType", "GROUP").param("ownerId", groupId.toString())
                        .param("from", from).param("to", to).with(bearer(owner)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[?(@.id == '" + eventId + "')]").exists());

        // Click the chip → EventModal edit → Save.
        mvc.perform(patch("/api/calendars/events/{id}", eventId)
                        .with(bearer(owner)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"description\":\"moved\",\"eventType\":\"DEADLINE\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.eventType").value("DEADLINE"))
                .andExpect(jsonPath("$.data.description").value("moved"));

        // Delete.
        mvc.perform(delete("/api/calendars/events/{id}", eventId).with(bearer(owner)))
                .andExpect(status().isOk());
        mvc.perform(get("/api/calendars/events/{id}", eventId).with(bearer(owner)))
                .andExpect(status().isNotFound());
    }

    @Test
    void event_starting_in_the_past_is_rejected() throws Exception {
        AuthedUser owner = registerEnrolled();
        UUID groupId = createPublicBubble(owner, "Past " + unique());

        // The modal's `min` guard mirrors the backend EVENT_STARTS_IN_PAST rule.
        mvc.perform(post("/api/calendars/events")
                        .with(bearer(owner)).contentType(MediaType.APPLICATION_JSON)
                        .content(String.format(
                                "{\"ownerType\":\"GROUP\",\"ownerId\":\"%s\",\"eventType\":\"STUDY_SESSION\",\"startsAt\":\"%s\",\"endsAt\":\"%s\"}",
                                groupId, Instant.now().minusSeconds(7200), Instant.now().minusSeconds(3600))))
                .andExpect(status().is4xxClientError())
                .andExpect(jsonPath("$.error.code").value("EVENT_STARTS_IN_PAST"));
    }

    @Test
    void schedule_live_room_creates_a_room_gated_until_its_window_opens() throws Exception {
        AuthedUser owner = registerEnrolled();
        UUID groupId = createPublicBubble(owner, "Live " + unique());

        // ScheduleRoomModal → a STUDY_SESSION event. Start it 30 min out so it's
        // well before the 15-min open window (the "Opens in N min" disabled state).
        UUID eventId = createGroupEvent(owner, groupId, "STUDY_SESSION", minutesFromNow(30), minutesFromNow(90));

        // "Join live" resolves the room via the event id; before the window it's gated.
        mvc.perform(get("/api/rooms/by-event/{e}", eventId).with(bearer(owner)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("ROOM_NOT_YET_OPEN"));
    }

    @Test
    void enter_room_before_window_is_blocked_non_member_too() throws Exception {
        AuthedUser owner = registerEnrolled();
        UUID groupId = createPublicBubble(owner, "Gate " + unique());
        UUID eventId = createGroupEvent(owner, groupId, "STUDY_SESSION", minutesFromNow(30), minutesFromNow(90));

        // A non-member resolving the room is rejected (membership gate or open-window gate).
        AuthedUser outsider = registerEnrolled();
        mvc.perform(get("/api/rooms/by-event/{e}", eventId).with(bearer(outsider)))
                .andExpect(status().isForbidden());
    }

    @Test
    void live_groups_endpoint_is_reachable_for_the_sidebar_marker() throws Exception {
        AuthedUser owner = registerEnrolled();
        createPublicBubble(owner, "Marker " + unique());

        // The hub polls this for the red "live now" marker; a future event isn't live yet.
        JsonNode live = getData(owner, "/api/rooms/live-groups");
        assertThat(live.isArray()).isTrue();
    }
}
