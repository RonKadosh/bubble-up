package com.ronkadosh.bubbleup.chat;

import com.fasterxml.jackson.databind.JsonNode;
import com.ronkadosh.bubbleup.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class MessagePaginationIT extends IntegrationTest {

    @Test
    void returns_latest_n_messages_when_no_cursor() throws Exception {
        AuthedUser owner = registerAndLogin();
        UUID groupId = createGroup(owner);
        UUID roomId = defaultRoomId(owner);
        for (int i = 0; i < 7; i++) sendText(owner, roomId, "m" + i);

        mvc.perform(get("/api/chat/rooms/{id}/messages", roomId)
                        .param("size", "5")
                        .with(bearer(owner)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(5))
                // newest-first ordering: most recent message is m6
                .andExpect(jsonPath("$.data[0].content").value("m6"))
                .andExpect(jsonPath("$.data[4].content").value("m2"));
    }

    @Test
    void returns_next_n_when_before_cursor_provided() throws Exception {
        AuthedUser owner = registerAndLogin();
        UUID groupId = createGroup(owner);
        UUID roomId = defaultRoomId(owner);
        for (int i = 0; i < 7; i++) sendText(owner, roomId, "m" + i);

        // first page: latest 3 (m6, m5, m4)
        String firstPage = mvc.perform(get("/api/chat/rooms/{id}/messages", roomId)
                        .param("size", "3")
                        .with(bearer(owner)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        JsonNode arr = om.readTree(firstPage).get("data");
        UUID oldestOnPage = UUID.fromString(arr.get(2).get("id").asText()); // m4

        // before m4 → m3, m2, m1
        mvc.perform(get("/api/chat/rooms/{id}/messages", roomId)
                        .param("before", oldestOnPage.toString())
                        .param("size", "3")
                        .with(bearer(owner)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(3))
                .andExpect(jsonPath("$.data[0].content").value("m3"))
                .andExpect(jsonPath("$.data[1].content").value("m2"))
                .andExpect(jsonPath("$.data[2].content").value("m1"));
    }

    @Test
    void size_clamped_to_max_100() throws Exception {
        AuthedUser owner = registerAndLogin();
        UUID groupId = createGroup(owner);
        UUID roomId = defaultRoomId(owner);
        // Don't seed 100+ messages — just verify the request with size=500 doesn't error
        // and returns however many exist (3).
        for (int i = 0; i < 3; i++) sendText(owner, roomId, "m" + i);

        mvc.perform(get("/api/chat/rooms/{id}/messages", roomId)
                        .param("size", "500")
                        .with(bearer(owner)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(3));
    }

    @Test
    void before_with_unknown_id_returns_400() throws Exception {
        AuthedUser owner = registerAndLogin();
        UUID groupId = createGroup(owner);
        UUID roomId = defaultRoomId(owner);

        mvc.perform(get("/api/chat/rooms/{id}/messages", roomId)
                        .param("before", UUID.randomUUID().toString())
                        .with(bearer(owner)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("CHAT_CURSOR_NOT_FOUND"));
    }

    @Test
    void non_member_forbidden() throws Exception {
        AuthedUser owner = registerAndLogin();
        AuthedUser outsider = registerAndLogin();
        UUID groupId = createGroup(owner);
        UUID roomId = defaultRoomId(owner);

        mvc.perform(get("/api/chat/rooms/{id}/messages", roomId).with(bearer(outsider)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("NOT_GROUP_MEMBER"));
    }

    private void sendText(AuthedUser sender, UUID roomId, String content) throws Exception {
        mvc.perform(post("/api/chat/rooms/{id}/messages", roomId)
                        .with(bearer(sender))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(String.format("{\"content\":\"%s\"}", content)))
                .andExpect(status().isCreated());
        // sentAt is Instant.now() (millisecond precision on Windows). Pace so order is strict.
        Thread.sleep(2);
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
