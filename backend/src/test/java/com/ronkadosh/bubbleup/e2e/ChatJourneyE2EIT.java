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
 * FLOWS §6 (Chat panel): send, reply, pin/unpin, unread + mark-read, share a
 * calendar link, and the poll create → vote → close lifecycle.
 */
class ChatJourneyE2EIT extends E2EFlowTest {

    @Test
    void send_then_reply_quotes_the_parent() throws Exception {
        AuthedUser owner = registerEnrolled();
        createPublicBubble(owner, "Chat " + unique());
        UUID roomId = defaultRoomId(owner);

        UUID parent = sendText(owner, roomId, "original");

        // Hover ↩ Reply → send with replyToMessageId.
        JsonNode reply = dataOf(mvc.perform(post("/api/chat/rooms/{id}/messages", roomId)
                        .with(bearer(owner)).contentType(MediaType.APPLICATION_JSON)
                        .content(String.format(
                                "{\"type\":\"TEXT\",\"content\":\"a reply\",\"replyToMessageId\":\"%s\"}", parent)))
                .andExpect(status().isCreated()));
        assertThat(reply.get("replyToMessageId").asText()).isEqualTo(parent.toString());
    }

    @Test
    void pin_and_unpin_surface_in_the_pins_list() throws Exception {
        AuthedUser owner = registerEnrolled();
        createPublicBubble(owner, "Pin " + unique());
        UUID roomId = defaultRoomId(owner);
        UUID msg = sendText(owner, roomId, "pin me");

        mvc.perform(post("/api/chat/rooms/{r}/messages/{m}/pin", roomId, msg).with(bearer(owner)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.pinned").value(true));
        mvc.perform(get("/api/chat/rooms/{r}/pins", roomId).with(bearer(owner)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[?(@.id == '" + msg + "')]").exists());

        mvc.perform(delete("/api/chat/rooms/{r}/messages/{m}/pin", roomId, msg).with(bearer(owner)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.pinned").value(false));
        mvc.perform(get("/api/chat/rooms/{r}/pins", roomId).with(bearer(owner)))
                .andExpect(jsonPath("$.data[?(@.id == '" + msg + "')]").doesNotExist());
    }

    @Test
    void unread_badge_grows_then_clears_on_mark_read() throws Exception {
        AuthedUser owner = registerEnrolled();
        AuthedUser joiner = registerEnrolled();
        UUID groupId = createPublicBubble(owner, "Unread " + unique());
        joinBubble(joiner, groupId);
        UUID roomId = defaultRoomId(owner);

        sendText(owner, roomId, "a");
        sendText(owner, roomId, "b");
        UUID latest = sendText(owner, roomId, "c");

        // Sidebar badge for the joiner = 3 unread (SYSTEM_JOIN doesn't count).
        JsonNode rooms = getData(joiner, "/api/chat/rooms");
        assertThat(rooms.get(0).get("unreadCount").asInt()).isEqualTo(3);

        // ChatPanel marks read at the bottom → badge clears.
        markRead(joiner, roomId, latest);
        assertThat(getData(joiner, "/api/chat/rooms").get(0).get("unreadCount").asInt()).isZero();
    }

    @Test
    void cursor_pagination_returns_older_pages() throws Exception {
        AuthedUser owner = registerEnrolled();
        createPublicBubble(owner, "Page " + unique());
        UUID roomId = defaultRoomId(owner);
        sendText(owner, roomId, "m1");
        sendText(owner, roomId, "m2");
        UUID m3 = sendText(owner, roomId, "m3");

        // size=2 returns the latest 2 (DESC); `before` fetches the older page.
        JsonNode firstPage = getData(owner, "/api/chat/rooms/" + roomId + "/messages?size=2");
        assertThat(firstPage).hasSize(2);
        UUID oldestLoaded = UUID.fromString(firstPage.get(firstPage.size() - 1).get("id").asText());
        JsonNode older = getData(owner,
                "/api/chat/rooms/" + roomId + "/messages?size=2&before=" + oldestLoaded);
        assertThat(older.size()).isGreaterThanOrEqualTo(1);
        // m3 is the newest, so it must be on the first page, never the older page.
        assertThat(older.toString()).doesNotContain(m3.toString());
    }

    @Test
    void share_calendar_event_to_chat_as_link_card() throws Exception {
        AuthedUser owner = registerEnrolled();
        UUID groupId = createPublicBubble(owner, "Link " + unique());
        UUID roomId = defaultRoomId(owner);
        UUID eventId = createGroupEvent(owner, groupId, "STUDY_SESSION", hoursFromNow(1), hoursFromNow(2));

        // Calendar "Share to chat" → sendLinkMessage(CALENDAR_EVENT).
        mvc.perform(post("/api/chat/rooms/{id}/messages", roomId)
                        .with(bearer(owner)).contentType(MediaType.APPLICATION_JSON)
                        .content(String.format(
                                "{\"type\":\"LINK\",\"linkTargetType\":\"CALENDAR_EVENT\",\"linkTargetId\":\"%s\",\"content\":\"join us\"}",
                                eventId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.messageType").value("LINK"))
                .andExpect(jsonPath("$.data.linkTargetType").value("CALENDAR_EVENT"))
                .andExpect(jsonPath("$.data.linkTargetId").value(eventId.toString()));
    }

    @Test
    void poll_create_vote_then_close() throws Exception {
        AuthedUser owner = registerEnrolled();
        createPublicBubble(owner, "Poll " + unique());
        UUID roomId = defaultRoomId(owner);

        // Composer "+" → Create poll.
        JsonNode poll = dataOf(mvc.perform(post("/api/chat/rooms/{id}/polls", roomId)
                        .with(bearer(owner)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"question\":\"Meet when?\",\"options\":[\"Mon\",\"Tue\"],\"allowMultiple\":false}"))
                .andExpect(status().isCreated()));
        UUID pollId = UUID.fromString(poll.get("id").asText());
        UUID optionId = UUID.fromString(poll.get("options").get(0).get("id").asText());

        // Vote on the poll card.
        mvc.perform(post("/api/chat/polls/{id}/vote", pollId)
                        .with(bearer(owner)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"optionIds\":[\"" + optionId + "\"]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalVotes").value(1));

        // Creator closes the poll.
        mvc.perform(post("/api/chat/polls/{id}/close", pollId).with(bearer(owner)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.closedAt").isNotEmpty());
    }

    @Test
    void non_member_cannot_send() throws Exception {
        AuthedUser owner = registerEnrolled();
        AuthedUser outsider = registerEnrolled();
        createPublicBubble(owner, "Closed " + unique());
        UUID roomId = defaultRoomId(owner);

        mvc.perform(post("/api/chat/rooms/{id}/messages", roomId)
                        .with(bearer(outsider)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"TEXT\",\"content\":\"intruder\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("NOT_GROUP_MEMBER"));
    }
}
