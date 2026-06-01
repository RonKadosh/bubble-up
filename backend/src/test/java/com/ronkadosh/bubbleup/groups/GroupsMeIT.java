package com.ronkadosh.bubbleup.groups;

import com.fasterxml.jackson.databind.JsonNode;
import com.ronkadosh.bubbleup.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class GroupsMeIT extends IntegrationTest {

    @Test
    void returns_empty_for_user_with_no_memberships() throws Exception {
        AuthedUser me = registerAndLogin();
        mvc.perform(get("/api/groups/me").with(bearer(me)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(0));
    }

    @Test
    void returns_only_groups_the_user_joined() throws Exception {
        AuthedUser owner = registerWithAffiliation();
        UUID courseId = seedCourseId();
        // Owner creates a public group on the seeded course.
        String create = String.format(
                "{\"name\":\"Solo\",\"description\":\"\",\"visibility\":\"PUBLIC\",\"maxMembers\":6,\"courseId\":\"%s\"}",
                courseId);
        MvcResult created = mvc.perform(post("/api/groups")
                        .with(bearer(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(create))
                .andExpect(status().isCreated())
                .andReturn();
        JsonNode data = om.readTree(created.getResponse().getContentAsString()).get("data");
        UUID groupId = UUID.fromString(data.get("id").asText());

        // Owner sees one group in /me.
        mvc.perform(get("/api/groups/me").with(bearer(owner)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].id").value(groupId.toString()));

        // A second user who hasn't joined sees zero.
        AuthedUser stranger = registerAndLogin();
        mvc.perform(get("/api/groups/me").with(bearer(stranger)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(0));

        // After the second user joins, they see one.
        mvc.perform(post("/api/groups/" + groupId + "/join").with(bearer(stranger)))
                .andExpect(status().isOk());
        mvc.perform(get("/api/groups/me").with(bearer(stranger)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1));
    }
}
