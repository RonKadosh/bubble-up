package com.ronkadosh.bubbleup.feed;

import com.fasterxml.jackson.databind.JsonNode;
import com.ronkadosh.bubbleup.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class FeedControllerIT extends IntegrationTest {

    @Test
    void feed_groups_activity_and_upcoming_into_sections() throws Exception {
        AuthedUser owner = registerAndLogin();
        AuthedUser joiner = registerAndLogin();
        UUID groupId = createGroup(owner);

        // A self-join posts a SYSTEM_JOIN (→ membership event + unread for the owner).
        mvc.perform(post("/api/groups/{id}/join", groupId).with(bearer(joiner)))
                .andExpect(status().isOk());
        // A future event (→ Upcoming).
        createEvent(owner, groupId, "MEETING", futurePlus(1), futurePlus(2));

        String json = mvc.perform(get("/api/feed").with(bearer(owner)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        JsonNode data = om.readTree(json).get("data");

        JsonNode upcoming = section(data, "UPCOMING");
        assertThat(upcoming).as("UPCOMING section present").isNotNull();
        assertThat(upcoming.get("items")).isNotEmpty();
        assertThat(upcoming.get("items").get(0).get("eventType").asText()).isEqualTo("MEETING");

        JsonNode activity = section(data, "ACTIVITY");
        assertThat(activity).as("ACTIVITY section present").isNotNull();
        assertThat(kinds(activity)).contains("memberJoin");
        // The owner hasn't read the join message → an unread rollup card is present too.
        assertThat(kinds(activity)).contains("unread");
    }

    @Test
    void feed_requires_authentication() throws Exception {
        mvc.perform(get("/api/feed"))
                .andExpect(status().isUnauthorized());
    }

    /** Returns the section node with the given key, or null if absent. */
    private static JsonNode section(JsonNode data, String key) {
        for (JsonNode s : data.get("sections")) {
            if (key.equals(s.get("key").asText())) return s;
        }
        return null;
    }

    private static java.util.List<String> kinds(JsonNode section) {
        java.util.List<String> out = new java.util.ArrayList<>();
        section.get("items").forEach(i -> out.add(i.get("kind").asText()));
        return out;
    }

    private UUID createGroup(AuthedUser owner) throws Exception {
        String json = mvc.perform(post("/api/groups")
                        .with(bearer(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"g\",\"visibility\":\"PUBLIC\",\"offeringId\":\"" + seedOfferingId() + "\"}"))
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
