package com.ronkadosh.bubbleup.admin;

import com.ronkadosh.bubbleup.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Flips {@code app.expert.auto-verify} off for this test so an applicant lands
 * in PENDING and the admin verify endpoint flips them to VERIFIED.
 */
@TestPropertySource(properties = "app.expert.auto-verify=false")
class AdminExpertVerificationIT extends IntegrationTest {

    @Test
    void apply_lands_pending_then_admin_verifies() throws Exception {
        AuthedUser admin = registerAndLoginAsAdmin();
        AuthedUser applicant = registerAndLogin();

        // Apply (as the applicant).
        String applyBody = "{\"headline\":\"PhD in CS\",\"bio\":\"Long bio\",\"expertiseTags\":[\"AI\"]}";
        mvc.perform(post("/api/experts/apply")
                        .with(bearer(applicant))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(applyBody))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.verificationStatus").value("PENDING"));

        // Admin lists pending experts.
        mvc.perform(get("/api/admin/experts?status=PENDING").with(bearer(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].verificationStatus").value("PENDING"));

        // Admin verifies.
        mvc.perform(post("/api/admin/experts/" + applicant.id() + "/verify")
                        .with(bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"Looks legit\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.verificationStatus").value("VERIFIED"))
                .andExpect(jsonPath("$.data.verifiedBy").value(admin.id().toString()));
    }

    @Test
    void admin_can_revoke_a_verified_expert() throws Exception {
        AuthedUser admin = registerAndLoginAsAdmin();
        AuthedUser applicant = registerAndLogin();

        String applyBody = "{\"headline\":\"Pro\",\"bio\":\"\",\"expertiseTags\":[]}";
        mvc.perform(post("/api/experts/apply")
                        .with(bearer(applicant))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(applyBody))
                .andExpect(status().isCreated());

        mvc.perform(post("/api/admin/experts/" + applicant.id() + "/verify")
                        .with(bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk());

        mvc.perform(post("/api/admin/experts/" + applicant.id() + "/revoke")
                        .with(bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"Misrepresentation\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.verificationStatus").value("PENDING"))
                .andExpect(jsonPath("$.data.verifiedAt").doesNotExist());
    }
}
