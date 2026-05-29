package com.ronkadosh.bubbleup.admin;

import com.ronkadosh.bubbleup.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AdminUserControllerIT extends IntegrationTest {

    @Test
    void student_cannot_list_users() throws Exception {
        AuthedUser student = registerAndLogin();
        mvc.perform(get("/api/admin/users").with(bearer(student)))
                .andExpect(status().isForbidden());
    }

    @Test
    void admin_can_list_users() throws Exception {
        AuthedUser admin = registerAndLoginAsAdmin();
        mvc.perform(get("/api/admin/users").with(bearer(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.content").isArray());
    }

    @Test
    void admin_can_change_other_user_role() throws Exception {
        AuthedUser admin = registerAndLoginAsAdmin();
        AuthedUser target = registerAndLogin();
        String body = "{\"newRole\":\"EXPERT\",\"reason\":\"Promote to expert for testing\"}";
        mvc.perform(patch("/api/admin/users/" + target.id() + "/role")
                        .with(bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.role").value("EXPERT"));
    }

    @Test
    void admin_cannot_change_own_role() throws Exception {
        AuthedUser admin = registerAndLoginAsAdmin();
        String body = "{\"newRole\":\"STUDENT\",\"reason\":\"Demote self\"}";
        mvc.perform(patch("/api/admin/users/" + admin.id() + "/role")
                        .with(bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("ADMIN_FORBIDDEN_SELF_ACTION"));
    }

    @Test
    void admin_can_promote_another_user_to_admin() throws Exception {
        AuthedUser admin = registerAndLoginAsAdmin();
        AuthedUser target = registerAndLogin();
        String body = "{\"newRole\":\"ADMIN\",\"reason\":\"Adding second admin\"}";
        mvc.perform(patch("/api/admin/users/" + target.id() + "/role")
                        .with(bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.role").value("ADMIN"));
    }

    // Note: The "last admin protected" guard is unreachable in Phase A's pure
    // role-change flow — to demote another admin you must be an admin yourself,
    // so at the moment of the check at least one admin always remains.
    // Self-demote is caught earlier by ADMIN_FORBIDDEN_SELF_ACTION. The guard
    // matters in Phase B once ban / soft-delete can also strip admin status.

    @Test
    void change_role_requires_reason() throws Exception {
        AuthedUser admin = registerAndLoginAsAdmin();
        AuthedUser target = registerAndLogin();
        String body = "{\"newRole\":\"EXPERT\",\"reason\":\"\"}";
        mvc.perform(patch("/api/admin/users/" + target.id() + "/role")
                        .with(bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
    }
}
