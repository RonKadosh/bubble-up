package com.ronkadosh.bubbleup.chat;

import com.ronkadosh.bubbleup.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class LinkMessageIT extends IntegrationTest {

    @Test
    void member_can_send_link_message_to_calendar_event() throws Exception {
        AuthedUser owner = registerAndLogin();
        UUID groupId = createGroup(owner);
        UUID roomId = defaultRoomId(owner);
        UUID eventId = UUID.randomUUID();   // backend doesn't validate the target; FE resolves.

        mvc.perform(post("/api/chat/rooms/{id}/messages", roomId)
                        .with(bearer(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(String.format(
                                "{\"type\":\"LINK\",\"content\":\"check this out\",\"linkTargetType\":\"CALENDAR_EVENT\",\"linkTargetId\":\"%s\"}",
                                eventId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.messageType").value("LINK"))
                .andExpect(jsonPath("$.data.linkTargetType").value("CALENDAR_EVENT"))
                .andExpect(jsonPath("$.data.linkTargetId").value(eventId.toString()))
                .andExpect(jsonPath("$.data.content").value("check this out"));
    }

    @Test
    void link_with_nonexistent_target_id_still_accepted() throws Exception {
        // Locked decision: chat does not validate the target exists. FE renders "Link unavailable"
        // on resolver 404/403. This keeps chat decoupled from every future linkable module.
        AuthedUser owner = registerAndLogin();
        UUID groupId = createGroup(owner);
        UUID roomId = defaultRoomId(owner);

        mvc.perform(post("/api/chat/rooms/{id}/messages", roomId)
                        .with(bearer(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(String.format(
                                "{\"type\":\"LINK\",\"linkTargetType\":\"CALENDAR_EVENT\",\"linkTargetId\":\"%s\"}",
                                UUID.randomUUID())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.messageType").value("LINK"));
    }

    @Test
    void link_without_link_target_id_rejected() throws Exception {
        AuthedUser owner = registerAndLogin();
        UUID groupId = createGroup(owner);
        UUID roomId = defaultRoomId(owner);

        mvc.perform(post("/api/chat/rooms/{id}/messages", roomId)
                        .with(bearer(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"LINK\",\"linkTargetType\":\"CALENDAR_EVENT\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
    }

    @Test
    void link_without_link_target_type_rejected() throws Exception {
        AuthedUser owner = registerAndLogin();
        UUID groupId = createGroup(owner);
        UUID roomId = defaultRoomId(owner);

        mvc.perform(post("/api/chat/rooms/{id}/messages", roomId)
                        .with(bearer(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(String.format(
                                "{\"type\":\"LINK\",\"linkTargetId\":\"%s\"}", UUID.randomUUID())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
    }

    @Test
    void text_with_link_fields_rejected() throws Exception {
        AuthedUser owner = registerAndLogin();
        UUID groupId = createGroup(owner);
        UUID roomId = defaultRoomId(owner);

        mvc.perform(post("/api/chat/rooms/{id}/messages", roomId)
                        .with(bearer(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(String.format(
                                "{\"type\":\"TEXT\",\"content\":\"hi\",\"linkTargetType\":\"CALENDAR_EVENT\",\"linkTargetId\":\"%s\"}",
                                UUID.randomUUID())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
    }

    @Test
    void system_message_type_cannot_be_sent_via_http() throws Exception {
        AuthedUser owner = registerAndLogin();
        UUID groupId = createGroup(owner);
        UUID roomId = defaultRoomId(owner);

        mvc.perform(post("/api/chat/rooms/{id}/messages", roomId)
                        .with(bearer(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"SYSTEM_JOIN\",\"content\":\"sneaky\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
    }

    private UUID createGroup(AuthedUser owner) throws Exception {
        String json = mvc.perform(post("/api/groups")
                        .with(bearer(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"g\",\"maxMembers\":6,\"offeringId\":\"" + seedOfferingId() + "\"}"))
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
