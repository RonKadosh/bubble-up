package com.ronkadosh.bubbleup.onboarding;

import com.fasterxml.jackson.databind.JsonNode;
import com.ronkadosh.bubbleup.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class OnboardingControllerIT extends IntegrationTest {

    @Test
    void fresh_user_is_incomplete_with_no_setup_done() throws Exception {
        AuthedUser u = registerAndLogin();

        JsonNode data = fetchStatus(u);

        assertThat(data.get("complete").asBoolean()).isFalse();
        assertThat(data.get("studyBase").get("affiliationDone").asBoolean()).isFalse();
        assertThat(data.get("studyBase").get("coursesDone").asBoolean()).isFalse();
        assertThat(data.get("inBubble").asBoolean()).isFalse();
        // No Daily Drops answered yet → zero confidence, not unlocked.
        assertThat(data.get("reliability").get("answeredQuestions").asInt()).isZero();
        assertThat(data.get("reliability").get("matched").asBoolean()).isFalse();
        // Persisted UI state defaults — and a plain read must not create a row.
        assertThat(data.get("acknowledged")).isEmpty();
        assertThat(data.get("collapsed").asBoolean()).isFalse();
        assertThat(data.get("wizardLevel").asInt()).isEqualTo(1);
        assertThat(fetchStatus(u).get("acknowledged")).as("read does not create state").isEmpty();
    }

    @Test
    void wizard_level_persists() throws Exception {
        AuthedUser u = registerAndLogin();

        mvc.perform(put("/api/onboarding/level")
                        .with(bearer(u))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"level\":3}"))
                .andExpect(status().isOk());

        assertThat(fetchStatus(u).get("wizardLevel").asInt()).isEqualTo(3);
    }

    @Test
    void wizard_level_rejects_out_of_range() throws Exception {
        AuthedUser u = registerAndLogin();
        mvc.perform(put("/api/onboarding/level")
                        .with(bearer(u))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"level\":7}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void acknowledgements_persist_and_appear_in_status() throws Exception {
        AuthedUser u = registerAndLogin();

        ack(u, "explainer:whatIsBubble");
        ack(u, "guide:enroll");

        JsonNode acknowledged = fetchStatus(u).get("acknowledged");
        assertThat(toStrings(acknowledged))
                .containsExactlyInAnyOrder("explainer:whatIsBubble", "guide:enroll");
    }

    @Test
    void collapsed_preference_persists() throws Exception {
        AuthedUser u = registerAndLogin();

        mvc.perform(put("/api/onboarding/collapsed")
                        .with(bearer(u))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"collapsed\":true}"))
                .andExpect(status().isOk());

        assertThat(fetchStatus(u).get("collapsed").asBoolean()).isTrue();
    }

    @Test
    void ack_rejects_blank_key() throws Exception {
        AuthedUser u = registerAndLogin();
        mvc.perform(post("/api/onboarding/ack")
                        .with(bearer(u))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"key\":\"\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void affiliated_and_enrolled_but_no_bubble_is_still_incomplete() throws Exception {
        AuthedUser u = registerEnrolled();

        JsonNode data = fetchStatus(u);

        assertThat(data.get("studyBase").get("affiliationDone").asBoolean()).isTrue();
        assertThat(data.get("studyBase").get("coursesDone").asBoolean()).isTrue();
        assertThat(data.get("inBubble").asBoolean()).isFalse();
        assertThat(data.get("complete").asBoolean()).isFalse();
    }

    @Test
    void onboarded_user_in_a_bubble_is_complete() throws Exception {
        AuthedUser u = registerEnrolled();
        createGroup(u); // creator is owner + member → in a bubble

        JsonNode data = fetchStatus(u);

        assertThat(data.get("inBubble").asBoolean()).isTrue();
        assertThat(data.get("complete").asBoolean()).isTrue();
    }

    @Test
    void status_requires_authentication() throws Exception {
        mvc.perform(get("/api/onboarding/status"))
                .andExpect(status().isUnauthorized());
    }

    private JsonNode fetchStatus(AuthedUser u) throws Exception {
        String json = mvc.perform(get("/api/onboarding/status").with(bearer(u)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return om.readTree(json).get("data");
    }

    private void ack(AuthedUser u, String key) throws Exception {
        mvc.perform(post("/api/onboarding/ack")
                        .with(bearer(u))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"key\":\"" + key + "\"}"))
                .andExpect(status().isOk());
    }

    private static java.util.List<String> toStrings(JsonNode arr) {
        java.util.List<String> out = new java.util.ArrayList<>();
        arr.forEach(n -> out.add(n.asText()));
        return out;
    }

    private UUID createGroup(AuthedUser owner) throws Exception {
        String json = mvc.perform(post("/api/groups")
                        .with(bearer(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"g\",\"maxMembers\":6,\"visibility\":\"PUBLIC\",\"offeringId\":\""
                                + seedOfferingId() + "\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return UUID.fromString(om.readTree(json).get("data").get("id").asText());
    }
}
