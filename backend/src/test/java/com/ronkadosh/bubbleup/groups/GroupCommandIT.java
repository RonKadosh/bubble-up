package com.ronkadosh.bubbleup.groups;

import com.ronkadosh.bubbleup.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class GroupCommandIT extends IntegrationTest {

    @Test
    void create_public_group_defaults_to_PUBLIC_and_count_1() throws Exception {
        AuthedUser owner = registerAndLogin();
        mvc.perform(post("/api/groups")
                        .with(bearer(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Algebra\",\"description\":\"linear stuff\",\"maxMembers\":6,\"offeringId\":\"" + seedOfferingId() + "\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.name").value("Algebra"))
                .andExpect(jsonPath("$.data.visibility").value("PUBLIC"))
                .andExpect(jsonPath("$.data.ownerId").value(owner.id().toString()))
                .andExpect(jsonPath("$.data.memberCount").value(1));
    }

    @Test
    void create_group_exposes_chosen_maxMembers() throws Exception {
        AuthedUser owner = registerAndLogin();
        mvc.perform(post("/api/groups")
                        .with(bearer(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Capped\",\"maxMembers\":7,\"offeringId\":\"" + seedOfferingId() + "\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.maxMembers").value(7));
    }

    @Test
    void create_group_with_maxMembers_below_min_is_rejected() throws Exception {
        AuthedUser owner = registerAndLogin();
        mvc.perform(post("/api/groups")
                        .with(bearer(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"TooSmall\",\"maxMembers\":3,\"offeringId\":\"" + seedOfferingId() + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
    }

    @Test
    void create_group_with_maxMembers_above_max_is_rejected() throws Exception {
        AuthedUser owner = registerAndLogin();
        mvc.perform(post("/api/groups")
                        .with(bearer(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"TooBig\",\"maxMembers\":11,\"offeringId\":\"" + seedOfferingId() + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
    }

    @Test
    void create_private_group_respects_visibility() throws Exception {
        AuthedUser owner = registerAndLogin();
        mvc.perform(post("/api/groups")
                        .with(bearer(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Secret\",\"visibility\":\"PRIVATE\",\"maxMembers\":6,\"offeringId\":\"" + seedOfferingId() + "\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.visibility").value("PRIVATE"));
    }

    @Test
    void get_by_id_returns_group() throws Exception {
        AuthedUser owner = registerAndLogin();
        UUID groupId = createGroup(owner, "Calc", "PUBLIC");
        mvc.perform(get("/api/groups/{id}", groupId).with(bearer(owner)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(groupId.toString()))
                .andExpect(jsonPath("$.data.name").value("Calc"));
    }

    @Test
    void get_by_id_not_found_returns_404() throws Exception {
        AuthedUser owner = registerAndLogin();
        mvc.perform(get("/api/groups/{id}", UUID.randomUUID()).with(bearer(owner)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("GROUP_NOT_FOUND"));
    }

    @Test
    void update_changes_name_and_visibility() throws Exception {
        AuthedUser owner = registerAndLogin();
        UUID groupId = createGroup(owner, "Old", "PUBLIC");
        mvc.perform(patch("/api/groups/{id}", groupId)
                        .with(bearer(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"New\",\"visibility\":\"PRIVATE\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("New"))
                .andExpect(jsonPath("$.data.visibility").value("PRIVATE"));
    }

    @Test
    void update_by_non_owner_rejected() throws Exception {
        AuthedUser owner = registerAndLogin();
        AuthedUser other = registerAndLogin();
        UUID groupId = createGroup(owner, "Mine", "PUBLIC");
        mvc.perform(patch("/api/groups/{id}", groupId)
                        .with(bearer(other))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Hacked\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("NOT_GROUP_OWNER"));
    }

    @Test
    void delete_empty_group_succeeds() throws Exception {
        AuthedUser owner = registerAndLogin();
        UUID groupId = createGroup(owner, "Solo", "PUBLIC");
        mvc.perform(delete("/api/groups/{id}", groupId).with(bearer(owner)))
                .andExpect(status().isOk());
        mvc.perform(get("/api/groups/{id}", groupId).with(bearer(owner)))
                .andExpect(status().isNotFound());
    }

    @Test
    void create_group_auto_creates_default_chat_room() throws Exception {
        AuthedUser owner = registerAndLogin();
        UUID groupId = createGroup(owner, "Algebra", "PUBLIC");
        mvc.perform(get("/api/chat/rooms").with(bearer(owner)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].name").value("general"))
                .andExpect(jsonPath("$.data[0].groupId").value(groupId.toString()));
    }

    @Test
    void delete_non_empty_group_rejected() throws Exception {
        AuthedUser owner = registerAndLogin();
        AuthedUser joiner = registerAndLogin();
        UUID groupId = createGroup(owner, "Crowd", "PUBLIC");
        mvc.perform(post("/api/groups/{id}/join", groupId).with(bearer(joiner)))
                .andExpect(status().isOk());
        mvc.perform(delete("/api/groups/{id}", groupId).with(bearer(owner)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("GROUP_NOT_EMPTY"));
    }

    private UUID createGroup(AuthedUser owner, String name, String visibility) throws Exception {
        String json = mvc.perform(post("/api/groups")
                        .with(bearer(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(String.format("{\"name\":\"%s\",\"visibility\":\"%s\",\"maxMembers\":6,\"offeringId\":\"%s\"}", name, visibility, seedOfferingId())))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return UUID.fromString(om.readTree(json).get("data").get("id").asText());
    }
}
