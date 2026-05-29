package com.ronkadosh.bubbleup.admin;

import com.ronkadosh.bubbleup.support.IntegrationTest;
import org.junit.jupiter.api.Test;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AdminOverviewControllerIT extends IntegrationTest {

    @Test
    void admin_can_fetch_overview() throws Exception {
        AuthedUser admin = registerAndLoginAsAdmin();
        mvc.perform(get("/api/admin/overview").with(bearer(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.kpis.totalUsers").isNumber())
                .andExpect(jsonPath("$.data.kpis.totalGroups").isNumber())
                .andExpect(jsonPath("$.data.kpis.totalCourses").isNumber())
                .andExpect(jsonPath("$.data.kpis.verifiedExperts").isNumber())
                .andExpect(jsonPath("$.data.roleDistribution.ADMIN").isNumber())
                .andExpect(jsonPath("$.data.recentActivity").isArray());
    }

    @Test
    void student_cannot_fetch_overview() throws Exception {
        AuthedUser student = registerAndLogin();
        mvc.perform(get("/api/admin/overview").with(bearer(student)))
                .andExpect(status().isForbidden());
    }
}
