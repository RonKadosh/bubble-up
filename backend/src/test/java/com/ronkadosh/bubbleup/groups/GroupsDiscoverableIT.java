package com.ronkadosh.bubbleup.groups;

import com.ronkadosh.bubbleup.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class GroupsDiscoverableIT extends IntegrationTest {

    @Test
    void lists_public_bubbles_in_my_enrolled_courses() throws Exception {
        AuthedUser owner = registerEnrolled();
        String name = "Discoverable " + UUID.randomUUID();
        createPublicBubble(owner, name);

        // A different enrolled user (not a member) sees that public bubble.
        AuthedUser me = registerEnrolled();
        mvc.perform(get("/api/groups/discoverable").with(bearer(me)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[?(@.name == '" + name + "')]").exists());
    }

    @Test
    void excludes_bubbles_the_caller_already_joined() throws Exception {
        AuthedUser me = registerEnrolled();
        String name = "Mine " + UUID.randomUUID();
        createPublicBubble(me, name); // creator is a member → must not appear

        mvc.perform(get("/api/groups/discoverable").with(bearer(me)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[?(@.name == '" + name + "')]").doesNotExist());
    }

    @Test
    void empty_without_affiliation_or_enrolment() throws Exception {
        AuthedUser fresh = registerAndLogin();
        mvc.perform(get("/api/groups/discoverable").with(bearer(fresh)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(0));
    }

    private void createPublicBubble(AuthedUser u, String name) throws Exception {
        mvc.perform(post("/api/groups")
                        .with(bearer(u))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(String.format(
                                "{\"name\":\"%s\",\"description\":\"\",\"visibility\":\"PUBLIC\",\"maxMembers\":6,\"courseId\":\"%s\"}",
                                name, seedOfferingCourseId())))
                .andExpect(status().isCreated());
    }
}
