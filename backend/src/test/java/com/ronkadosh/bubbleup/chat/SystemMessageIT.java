package com.ronkadosh.bubbleup.chat;

import com.ronkadosh.bubbleup.chat.model.ChatMessageType;
import com.ronkadosh.bubbleup.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.http.MediaType;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class SystemMessageIT extends IntegrationTest {

    /**
     * Regression guard: no SYSTEM_* type may be posted over HTTP. The validation
     * switch in ChatCommandService rejects every non-content type via a {@code default}
     * arm, so adding a new SYSTEM_* enum value can't silently reopen the spoof hole.
     * EXCLUDE filters down to the SYSTEM_* members — TEXT/LINK are the legitimate
     * client-sendable types and have their own happy-path coverage.
     */
    @ParameterizedTest
    @EnumSource(value = ChatMessageType.class, names = {"TEXT", "LINK"}, mode = EnumSource.Mode.EXCLUDE)
    void system_message_types_cannot_be_posted_via_http(ChatMessageType type) throws Exception {
        AuthedUser owner = registerEnrolled();
        createGroup(owner, "PUBLIC");
        UUID defaultRoomId = defaultRoomId(owner);

        mvc.perform(post("/api/chat/rooms/{id}/messages", defaultRoomId)
                        .with(bearer(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(String.format("{\"content\":\"spoofed\",\"type\":\"%s\"}", type)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));

        // Nothing was persisted — the room is still empty.
        mvc.perform(get("/api/chat/rooms/{id}/messages", defaultRoomId).with(bearer(owner)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(0));
    }

    @Test
    void join_emits_system_join_in_default_room() throws Exception {
        AuthedUser owner = registerEnrolled();
        AuthedUser joiner = registerEnrolled();
        UUID groupId = createGroup(owner, "PUBLIC");
        UUID defaultRoomId = defaultRoomId(owner);

        mvc.perform(post("/api/groups/{id}/join", groupId).with(bearer(joiner)))
                .andExpect(status().isOk());

        mvc.perform(get("/api/chat/rooms/{id}/messages", defaultRoomId).with(bearer(owner)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].messageType").value("SYSTEM_JOIN"))
                .andExpect(jsonPath("$.data[0].senderId").doesNotExist())
                .andExpect(jsonPath("$.data[0].subjectUserId").value(joiner.id().toString()))
                .andExpect(jsonPath("$.data[0].content").value(joiner.email()));
    }

    @Test
    void leave_emits_system_leave() throws Exception {
        AuthedUser owner = registerEnrolled();
        AuthedUser joiner = registerEnrolled();
        UUID groupId = createGroup(owner, "PUBLIC");
        UUID defaultRoomId = defaultRoomId(owner);

        mvc.perform(post("/api/groups/{id}/join", groupId).with(bearer(joiner)))
                .andExpect(status().isOk());
        mvc.perform(delete("/api/groups/{id}/members/me", groupId).with(bearer(joiner)))
                .andExpect(status().isOk());

        // newest-first: LEAVE then JOIN
        mvc.perform(get("/api/chat/rooms/{id}/messages", defaultRoomId).with(bearer(owner)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[0].messageType").value("SYSTEM_LEAVE"))
                .andExpect(jsonPath("$.data[0].subjectUserId").value(joiner.id().toString()))
                .andExpect(jsonPath("$.data[1].messageType").value("SYSTEM_JOIN"));
    }

    @Test
    void owner_create_does_not_emit_system_message() throws Exception {
        AuthedUser owner = registerEnrolled();
        createGroup(owner, "PUBLIC");
        UUID defaultRoomId = defaultRoomId(owner);

        mvc.perform(get("/api/chat/rooms/{id}/messages", defaultRoomId).with(bearer(owner)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(0));
    }

    @Test
    void owner_cascade_delete_does_not_spam_system_messages() throws Exception {
        // Owner is the only member; leaving cascades the delete. The plan calls out that
        // the cascade branch deliberately skips posting a SYSTEM_LEAVE (room is going away
        // anyway). Easiest assertion: no exception, group is gone, rooms list is empty.
        AuthedUser owner = registerEnrolled();
        UUID groupId = createGroup(owner, "PUBLIC");

        mvc.perform(delete("/api/groups/{id}/members/me", groupId).with(bearer(owner)))
                .andExpect(status().isOk());

        mvc.perform(get("/api/chat/rooms").with(bearer(owner)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(0));
    }

    @Test
    void owner_add_member_emits_system_join() throws Exception {
        // Owner-initiated add now announces the new member with the same SYSTEM_JOIN
        // row a self-join posts, so adding someone isn't a silent change.
        AuthedUser owner = registerEnrolled();
        AuthedUser invitee = registerEnrolled();
        UUID groupId = createGroup(owner, "PRIVATE");
        UUID defaultRoomId = defaultRoomId(owner);

        mvc.perform(post("/api/groups/{id}/members", groupId)
                        .with(bearer(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(String.format("{\"userId\":\"%s\"}", invitee.id())))
                .andExpect(status().isCreated());

        mvc.perform(get("/api/chat/rooms/{id}/messages", defaultRoomId).with(bearer(owner)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].messageType").value("SYSTEM_JOIN"))
                .andExpect(jsonPath("$.data[0].subjectUserId").value(invitee.id().toString()))
                .andExpect(jsonPath("$.data[0].content").value(invitee.email()));
    }

    private UUID createGroup(AuthedUser owner, String visibility) throws Exception {
        String json = mvc.perform(post("/api/groups")
                        .with(bearer(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(String.format("{\"name\":\"g\",\"maxMembers\":6,\"visibility\":\"%s\",\"offeringId\":\"%s\"}", visibility, seedOfferingId())))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return UUID.fromString(om.readTree(json).get("data").get("id").asText());
    }

    /** Auto-created "general" room — assumes the caller has exactly one group. */
    private UUID defaultRoomId(AuthedUser member) throws Exception {
        String json = mvc.perform(get("/api/chat/rooms").with(bearer(member)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return UUID.fromString(om.readTree(json).get("data").get(0).get("id").asText());
    }
}
