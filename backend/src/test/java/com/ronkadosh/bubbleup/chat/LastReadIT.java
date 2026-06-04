package com.ronkadosh.bubbleup.chat;

import com.fasterxml.jackson.databind.JsonNode;
import com.ronkadosh.bubbleup.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class LastReadIT extends IntegrationTest {

    @Test
    void no_cursor_means_all_unread() throws Exception {
        AuthedUser owner = registerEnrolled();
        AuthedUser joiner = registerEnrolled();
        UUID groupId = createGroup(owner);
        join(joiner, groupId);
        UUID roomId = defaultRoomId(owner);

        sendText(owner, roomId, "a");
        sendText(owner, roomId, "b");
        sendText(owner, roomId, "c");

        // joiner has never marked-read; the SYSTEM_JOIN doesn't count, so only the
        // 3 text messages are unread.
        mvc.perform(get("/api/chat/rooms").with(bearer(joiner)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].unreadCount").value(3));
    }

    @Test
    void posting_read_cursor_sets_unread_to_remaining() throws Exception {
        AuthedUser owner = registerEnrolled();
        AuthedUser joiner = registerEnrolled();
        UUID groupId = createGroup(owner);
        join(joiner, groupId);
        UUID roomId = defaultRoomId(owner);

        UUID m1 = sendText(owner, roomId, "a");
        sendText(owner, roomId, "b");
        sendText(owner, roomId, "c");

        markRead(joiner, roomId, m1);

        // SYSTEM_JOIN + m1 are at-or-before the cursor; b + c remain → 2 unread.
        mvc.perform(get("/api/chat/rooms").with(bearer(joiner)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].unreadCount").value(2));
    }

    @Test
    void posting_latest_clears_unread() throws Exception {
        AuthedUser owner = registerEnrolled();
        AuthedUser joiner = registerEnrolled();
        UUID groupId = createGroup(owner);
        join(joiner, groupId);
        UUID roomId = defaultRoomId(owner);

        sendText(owner, roomId, "a");
        sendText(owner, roomId, "b");
        UUID latest = sendText(owner, roomId, "c");

        markRead(joiner, roomId, latest);

        mvc.perform(get("/api/chat/rooms").with(bearer(joiner)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].unreadCount").value(0));
    }

    @Test
    void system_messages_do_not_count_as_unread() throws Exception {
        // SYSTEM_JOIN is emitted when joiner self-joins. It's a notice, not a chat
        // message — even with no read cursor it must not inflate the owner's unread
        // badge (the join is surfaced as dashboard activity instead).
        AuthedUser owner = registerEnrolled();
        AuthedUser joiner = registerEnrolled();
        UUID groupId = createGroup(owner);
        join(joiner, groupId);

        mvc.perform(get("/api/chat/rooms").with(bearer(owner)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].unreadCount").value(0));
    }

    @Test
    void non_member_cannot_post_read() throws Exception {
        AuthedUser owner = registerEnrolled();
        AuthedUser outsider = registerEnrolled();
        UUID groupId = createGroup(owner);
        UUID roomId = defaultRoomId(owner);
        UUID anyMsg = sendText(owner, roomId, "x");

        mvc.perform(post("/api/chat/rooms/{id}/read", roomId)
                        .with(bearer(outsider))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(String.format("{\"lastReadMessageId\":\"%s\"}", anyMsg)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("NOT_GROUP_MEMBER"));
    }

    private UUID sendText(AuthedUser sender, UUID roomId, String content) throws Exception {
        String json = mvc.perform(post("/api/chat/rooms/{id}/messages", roomId)
                        .with(bearer(sender))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(String.format("{\"content\":\"%s\"}", content)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        JsonNode data = om.readTree(json).get("data");
        // sentAt is Instant.now() (millisecond precision on Windows). Pace so strictly-greater
        // comparisons (used for unread counts) behave deterministically.
        Thread.sleep(2);
        return UUID.fromString(data.get("id").asText());
    }

    private void markRead(AuthedUser user, UUID roomId, UUID lastReadMessageId) throws Exception {
        mvc.perform(post("/api/chat/rooms/{id}/read", roomId)
                        .with(bearer(user))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(String.format("{\"lastReadMessageId\":\"%s\"}", lastReadMessageId)))
                .andExpect(status().isOk());
    }

    private void join(AuthedUser user, UUID groupId) throws Exception {
        mvc.perform(post("/api/groups/{id}/join", groupId).with(bearer(user)))
                .andExpect(status().isOk());
    }

    private UUID createGroup(AuthedUser owner) throws Exception {
        String json = mvc.perform(post("/api/groups")
                        .with(bearer(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"g\",\"maxMembers\":6,\"visibility\":\"PUBLIC\",\"offeringId\":\"" + seedOfferingId() + "\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return UUID.fromString(om.readTree(json).get("data").get("id").asText());
    }

    private UUID defaultRoomId(AuthedUser member) throws Exception {
        String json = mvc.perform(get("/api/chat/rooms").with(bearer(member)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return UUID.fromString(om.readTree(json).get("data").get(0).get("id").asText());
    }
}
