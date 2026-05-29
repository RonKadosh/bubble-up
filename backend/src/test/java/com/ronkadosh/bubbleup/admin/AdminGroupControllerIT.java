package com.ronkadosh.bubbleup.admin;

import com.ronkadosh.bubbleup.support.IntegrationTest;
import org.junit.jupiter.api.Test;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AdminGroupControllerIT extends IntegrationTest {

    @Test
    void student_cannot_list_groups() throws Exception {
        AuthedUser student = registerAndLogin();
        mvc.perform(get("/api/admin/groups").with(bearer(student)))
                .andExpect(status().isForbidden());
    }

    @Test
    void admin_can_list_groups() throws Exception {
        AuthedUser admin = registerAndLoginAsAdmin();
        mvc.perform(get("/api/admin/groups").with(bearer(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.content").isArray());
    }
}
