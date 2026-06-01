package com.ronkadosh.bubbleup.groups;

import com.ronkadosh.bubbleup.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import java.time.Instant;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class GroupEventsIT extends IntegrationTest {

    @Test
    void member_sees_group_events_through_proxy() throws Exception {
        AuthedUser owner = registerAndLogin();
        UUID groupId = createGroup(owner);
        createEvent(owner, groupId, "MEETING", futurePlus(1), futurePlus(2));

        Instant from = futurePlus(0);
        Instant to = futurePlus(10);
        mvc.perform(get("/api/groups/{id}/events", groupId)
                        .with(bearer(owner))
                        .param("from", from.toString())
                        .param("to", to.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].eventType").value("MEETING"));
    }

    @Test
    void non_member_cannot_list_group_events() throws Exception {
        AuthedUser owner = registerAndLogin();
        AuthedUser outsider = registerAndLogin();
        UUID groupId = createGroup(owner);

        mvc.perform(get("/api/groups/{id}/events", groupId).with(bearer(outsider)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("NOT_GROUP_MEMBER"));
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

    private void createEvent(AuthedUser u, UUID groupId, String type, Instant from, Instant to) throws Exception {
        mvc.perform(post("/api/calendars/events")
                        .with(bearer(u))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(String.format(
                                "{\"ownerType\":\"GROUP\",\"ownerId\":\"%s\",\"eventType\":\"%s\",\"startsAt\":\"%s\",\"endsAt\":\"%s\"}",
                                groupId, type, from, to)))
                .andExpect(status().isCreated());
    }

    private static Instant futurePlus(long hours) {
        return Instant.now().plusSeconds(hours * 3600);
    }
}
