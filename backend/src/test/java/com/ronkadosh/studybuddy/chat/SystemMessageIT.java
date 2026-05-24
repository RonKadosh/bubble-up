package com.ronkadosh.studybuddy.chat;

import com.ronkadosh.studybuddy.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class SystemMessageIT extends IntegrationTest {

    @Test
    void join_emits_system_join_in_default_room() throws Exception {
        AuthedUser owner = registerAndLogin();
        AuthedUser joiner = registerAndLogin();
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
        AuthedUser owner = registerAndLogin();
        AuthedUser joiner = registerAndLogin();
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
        AuthedUser owner = registerAndLogin();
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
        AuthedUser owner = registerAndLogin();
        UUID groupId = createGroup(owner, "PUBLIC");

        mvc.perform(delete("/api/groups/{id}/members/me", groupId).with(bearer(owner)))
                .andExpect(status().isOk());

        mvc.perform(get("/api/chat/rooms").with(bearer(owner)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(0));
    }

    @Test
    void owner_add_member_does_not_emit_join() throws Exception {
        // Owner-initiated add is not the same as a self-join; the plan intentionally
        // skips system messages for add (and kick/transfer/rename/visibility-change).
        AuthedUser owner = registerAndLogin();
        AuthedUser invitee = registerAndLogin();
        UUID groupId = createGroup(owner, "PRIVATE");
        UUID defaultRoomId = defaultRoomId(owner);

        mvc.perform(post("/api/groups/{id}/members", groupId)
                        .with(bearer(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(String.format("{\"userId\":\"%s\"}", invitee.id())))
                .andExpect(status().isCreated());

        mvc.perform(get("/api/chat/rooms/{id}/messages", defaultRoomId).with(bearer(owner)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(0));
    }

    private UUID createGroup(AuthedUser owner, String visibility) throws Exception {
        String json = mvc.perform(post("/api/groups")
                        .with(bearer(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(String.format("{\"name\":\"g\",\"visibility\":\"%s\"}", visibility)))
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
