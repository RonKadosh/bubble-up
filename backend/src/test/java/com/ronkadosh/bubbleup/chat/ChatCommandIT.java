package com.ronkadosh.bubbleup.chat;

import com.ronkadosh.bubbleup.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ChatCommandIT extends IntegrationTest {

    @Test
    void member_can_create_room_in_group() throws Exception {
        AuthedUser owner = registerAndLogin();
        UUID groupId = createGroup(owner);
        mvc.perform(post("/api/chat/rooms")
                        .with(bearer(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(String.format("{\"name\":\"general\",\"groupId\":\"%s\"}", groupId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.name").value("general"))
                .andExpect(jsonPath("$.data.groupId").value(groupId.toString()));
    }

    @Test
    void non_member_cannot_create_room() throws Exception {
        AuthedUser owner = registerAndLogin();
        AuthedUser outsider = registerAndLogin();
        UUID groupId = createGroup(owner);
        mvc.perform(post("/api/chat/rooms")
                        .with(bearer(outsider))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(String.format("{\"name\":\"sneaky\",\"groupId\":\"%s\"}", groupId)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("NOT_GROUP_MEMBER"));
    }

    @Test
    void member_can_send_and_retrieve_message() throws Exception {
        AuthedUser owner = registerAndLogin();
        UUID groupId = createGroup(owner);
        UUID roomId = createRoom(owner, groupId);
        mvc.perform(post("/api/chat/rooms/{id}/messages", roomId)
                        .with(bearer(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"hello\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.content").value("hello"));
        mvc.perform(get("/api/chat/rooms/{id}/messages", roomId).with(bearer(owner)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].content").value("hello"))
                .andExpect(jsonPath("$.data[0].messageType").value("TEXT"));
    }

    @Test
    void non_member_cannot_send_message() throws Exception {
        AuthedUser owner = registerAndLogin();
        AuthedUser outsider = registerAndLogin();
        UUID groupId = createGroup(owner);
        UUID roomId = createRoom(owner, groupId);
        mvc.perform(post("/api/chat/rooms/{id}/messages", roomId)
                        .with(bearer(outsider))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"intruder\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("NOT_GROUP_MEMBER"));
    }

    @Test
    void list_rooms_filtered_to_my_groups() throws Exception {
        // Each createGroup auto-creates a default "general" room. Adding a manual room
        // brings each group to 2 rooms; member A should see only A's 2 rooms (not B's).
        AuthedUser a = registerAndLogin();
        AuthedUser b = registerAndLogin();
        UUID groupA = createGroup(a);
        UUID groupB = createGroup(b);
        createRoom(a, groupA);
        createRoom(b, groupB);
        mvc.perform(get("/api/chat/rooms").with(bearer(a)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[0].groupId").value(groupA.toString()))
                .andExpect(jsonPath("$.data[1].groupId").value(groupA.toString()))
                .andExpect(jsonPath("$.data[0].unreadCount").value(0))
                .andExpect(jsonPath("$.data[1].unreadCount").value(0));
    }

    private UUID createGroup(AuthedUser owner) throws Exception {
        String json = mvc.perform(post("/api/groups")
                        .with(bearer(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"g\",\"offeringId\":\"" + seedOfferingId() + "\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return UUID.fromString(om.readTree(json).get("data").get("id").asText());
    }

    private UUID createRoom(AuthedUser owner, UUID groupId) throws Exception {
        String json = mvc.perform(post("/api/chat/rooms")
                        .with(bearer(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(String.format("{\"name\":\"r\",\"groupId\":\"%s\"}", groupId)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return UUID.fromString(om.readTree(json).get("data").get("id").asText());
    }
}
