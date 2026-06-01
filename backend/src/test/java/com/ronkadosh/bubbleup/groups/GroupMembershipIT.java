package com.ronkadosh.bubbleup.groups;

import com.ronkadosh.bubbleup.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class GroupMembershipIT extends IntegrationTest {

    @Test
    void anyone_can_join_public_group() throws Exception {
        AuthedUser owner = registerAndLogin();
        AuthedUser joiner = registerAndLogin();
        UUID groupId = createGroup(owner, "PUBLIC");
        mvc.perform(post("/api/groups/{id}/join", groupId).with(bearer(joiner)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.userId").value(joiner.id().toString()))
                .andExpect(jsonPath("$.data.role").value("MEMBER"));
    }

    @Test
    void joining_private_group_returns_GROUP_NOT_PUBLIC() throws Exception {
        AuthedUser owner = registerAndLogin();
        AuthedUser joiner = registerAndLogin();
        UUID groupId = createGroup(owner, "PRIVATE");
        mvc.perform(post("/api/groups/{id}/join", groupId).with(bearer(joiner)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("GROUP_NOT_PUBLIC"));
    }

    @Test
    void owner_can_add_member_to_private_group() throws Exception {
        AuthedUser owner = registerAndLogin();
        AuthedUser invitee = registerAndLogin();
        UUID groupId = createGroup(owner, "PRIVATE");
        mvc.perform(post("/api/groups/{id}/members", groupId)
                        .with(bearer(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(String.format("{\"userId\":\"%s\"}", invitee.id())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.userId").value(invitee.id().toString()));
    }

    @Test
    void non_owner_cannot_add_member() throws Exception {
        AuthedUser owner = registerAndLogin();
        AuthedUser other = registerAndLogin();
        AuthedUser target = registerAndLogin();
        UUID groupId = createGroup(owner, "PRIVATE");
        mvc.perform(post("/api/groups/{id}/members", groupId)
                        .with(bearer(other))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(String.format("{\"userId\":\"%s\"}", target.id())))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("NOT_GROUP_OWNER"));
    }

    @Test
    void adding_nonexistent_user_returns_USER_NOT_FOUND() throws Exception {
        AuthedUser owner = registerAndLogin();
        UUID groupId = createGroup(owner, "PRIVATE");
        mvc.perform(post("/api/groups/{id}/members", groupId)
                        .with(bearer(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(String.format("{\"userId\":\"%s\"}", UUID.randomUUID())))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("USER_NOT_FOUND"));
    }

    @Test
    void duplicate_join_returns_ALREADY_GROUP_MEMBER() throws Exception {
        AuthedUser owner = registerAndLogin();
        AuthedUser joiner = registerAndLogin();
        UUID groupId = createGroup(owner, "PUBLIC");
        mvc.perform(post("/api/groups/{id}/join", groupId).with(bearer(joiner)))
                .andExpect(status().isOk());
        mvc.perform(post("/api/groups/{id}/join", groupId).with(bearer(joiner)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("ALREADY_GROUP_MEMBER"));
    }

    @Test
    void members_list_visible_to_members_only() throws Exception {
        AuthedUser owner = registerAndLogin();
        AuthedUser nonMember = registerAndLogin();
        UUID groupId = createGroup(owner, "PUBLIC");
        mvc.perform(get("/api/groups/{id}/members", groupId).with(bearer(owner)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1));
        mvc.perform(get("/api/groups/{id}/members", groupId).with(bearer(nonMember)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("NOT_GROUP_MEMBER"));
    }

    @Test
    void member_can_leave() throws Exception {
        AuthedUser owner = registerAndLogin();
        AuthedUser joiner = registerAndLogin();
        UUID groupId = createGroup(owner, "PUBLIC");
        mvc.perform(post("/api/groups/{id}/join", groupId).with(bearer(joiner)))
                .andExpect(status().isOk());
        mvc.perform(delete("/api/groups/{id}/members/me", groupId).with(bearer(joiner)))
                .andExpect(status().isOk());
        mvc.perform(get("/api/groups/{id}/members", groupId).with(bearer(joiner)))
                .andExpect(status().isForbidden());
    }

    @Test
    void owner_cannot_leave_group_with_other_members() throws Exception {
        AuthedUser owner = registerAndLogin();
        AuthedUser joiner = registerAndLogin();
        UUID groupId = createGroup(owner, "PUBLIC");
        mvc.perform(post("/api/groups/{id}/join", groupId).with(bearer(joiner)))
                .andExpect(status().isOk());
        mvc.perform(delete("/api/groups/{id}/members/me", groupId).with(bearer(owner)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("OWNER_MUST_TRANSFER_OR_EMPTY"));
    }

    @Test
    void solo_owner_leaving_deletes_group() throws Exception {
        AuthedUser owner = registerAndLogin();
        UUID groupId = createGroup(owner, "PUBLIC");
        mvc.perform(delete("/api/groups/{id}/members/me", groupId).with(bearer(owner)))
                .andExpect(status().isOk());
        mvc.perform(get("/api/groups/{id}", groupId).with(bearer(owner)))
                .andExpect(status().isNotFound());
    }

    @Test
    void transfer_ownership_swaps_roles() throws Exception {
        AuthedUser owner = registerAndLogin();
        AuthedUser successor = registerAndLogin();
        UUID groupId = createGroup(owner, "PUBLIC");
        mvc.perform(post("/api/groups/{id}/join", groupId).with(bearer(successor)))
                .andExpect(status().isOk());
        mvc.perform(post("/api/groups/{id}/transfer-ownership", groupId)
                        .with(bearer(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(String.format("{\"newOwnerId\":\"%s\"}", successor.id())))
                .andExpect(status().isOk());
        mvc.perform(get("/api/groups/{id}", groupId).with(bearer(owner)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.ownerId").value(successor.id().toString()));
    }

    @Test
    void transfer_to_non_member_rejected() throws Exception {
        AuthedUser owner = registerAndLogin();
        AuthedUser outsider = registerAndLogin();
        UUID groupId = createGroup(owner, "PUBLIC");
        mvc.perform(post("/api/groups/{id}/transfer-ownership", groupId)
                        .with(bearer(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(String.format("{\"newOwnerId\":\"%s\"}", outsider.id())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("NEW_OWNER_NOT_GROUP_MEMBER"));
    }

    @Test
    void owner_can_remove_other_member_but_not_self() throws Exception {
        AuthedUser owner = registerAndLogin();
        AuthedUser joiner = registerAndLogin();
        UUID groupId = createGroup(owner, "PUBLIC");
        mvc.perform(post("/api/groups/{id}/join", groupId).with(bearer(joiner)))
                .andExpect(status().isOk());
        mvc.perform(delete("/api/groups/{id}/members/{userId}", groupId, joiner.id()).with(bearer(owner)))
                .andExpect(status().isOk());
        mvc.perform(delete("/api/groups/{id}/members/{userId}", groupId, owner.id()).with(bearer(owner)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("CANNOT_REMOVE_SELF_USE_LEAVE"));
    }

    @Test
    void joining_a_full_group_returns_GROUP_IS_FULL() throws Exception {
        AuthedUser owner = registerAndLogin();
        // maxMembers=4 → owner already occupies 1 slot, so 3 more joins fill it.
        UUID groupId = createGroup(owner, "PUBLIC", 4);
        for (int i = 0; i < 3; i++) {
            mvc.perform(post("/api/groups/{id}/join", groupId).with(bearer(registerAndLogin())))
                    .andExpect(status().isOk());
        }
        // The 5th member is rejected.
        mvc.perform(post("/api/groups/{id}/join", groupId).with(bearer(registerAndLogin())))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("GROUP_IS_FULL"));
    }

    @Test
    void owner_adding_member_to_full_group_returns_GROUP_IS_FULL() throws Exception {
        AuthedUser owner = registerAndLogin();
        UUID groupId = createGroup(owner, "PRIVATE", 4);
        for (int i = 0; i < 3; i++) {
            AuthedUser invitee = registerAndLogin();
            mvc.perform(post("/api/groups/{id}/members", groupId)
                            .with(bearer(owner))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(String.format("{\"userId\":\"%s\"}", invitee.id())))
                    .andExpect(status().isCreated());
        }
        AuthedUser overflow = registerAndLogin();
        mvc.perform(post("/api/groups/{id}/members", groupId)
                        .with(bearer(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(String.format("{\"userId\":\"%s\"}", overflow.id())))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("GROUP_IS_FULL"));
    }

    private UUID createGroup(AuthedUser owner, String visibility) throws Exception {
        return createGroup(owner, visibility, 6);
    }

    private UUID createGroup(AuthedUser owner, String visibility, int maxMembers) throws Exception {
        String json = mvc.perform(post("/api/groups")
                        .with(bearer(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(String.format("{\"name\":\"g\",\"maxMembers\":%d,\"visibility\":\"%s\",\"offeringId\":\"%s\"}", maxMembers, visibility, seedOfferingId())))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return UUID.fromString(om.readTree(json).get("data").get("id").asText());
    }
}
