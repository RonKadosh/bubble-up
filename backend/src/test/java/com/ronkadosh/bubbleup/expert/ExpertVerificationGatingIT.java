package com.ronkadosh.bubbleup.expert;

import com.ronkadosh.bubbleup.auth.persistence.UserRepository;
import com.ronkadosh.bubbleup.common.context.UserRole;
import com.ronkadosh.bubbleup.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * With {@code app.expert.auto-verify=false}, the EXPERT role + abilities are
 * granted only on admin approval. Verifies the full gate: apply leaves the user
 * a STUDENT in PENDING and unable to host; verify promotes + unlocks hosting;
 * revoke returns them to STUDENT.
 */
@TestPropertySource(properties = "app.expert.auto-verify=false")
class ExpertVerificationGatingIT extends IntegrationTest {

    @Autowired private UserRepository userRepository;

    private static String sessionBody() {
        Instant start = Instant.now().plus(Duration.ofHours(1));
        Instant end = Instant.now().plus(Duration.ofHours(2));
        return String.format("{\"title\":\"Gated Q&A\",\"startsAt\":\"%s\",\"endsAt\":\"%s\",\"capacity\":3}", start, end);
    }

    @Test
    void apply_stays_student_pending_then_verify_unlocks_hosting_and_revoke_relocks() throws Exception {
        AuthedUser admin = registerAndLoginAsAdmin();
        AuthedUser applicant = registerAndLogin();

        // Apply → PENDING, and the role must NOT have changed yet.
        mvc.perform(post("/api/experts/apply")
                        .with(bearer(applicant))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"headline\":\"Algorithms TA\",\"bio\":\"\",\"expertiseTags\":[]}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.verificationStatus").value("PENDING"));
        assertThat(roleOf(applicant)).isEqualTo(UserRole.STUDENT);

        // A pending applicant cannot host a session.
        mvc.perform(post("/api/expert-sessions")
                        .with(bearer(applicant))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(sessionBody()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("EXPERT_NOT_VERIFIED"));

        // Admin verifies → role flips to EXPERT.
        mvc.perform(post("/api/admin/experts/" + applicant.id() + "/verify")
                        .with(bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"checks out\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.verificationStatus").value("VERIFIED"));
        assertThat(roleOf(applicant)).isEqualTo(UserRole.EXPERT);

        // Now hosting works (the JWT still says STUDENT, but the gate is the DB profile status).
        mvc.perform(post("/api/expert-sessions")
                        .with(bearer(applicant))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(sessionBody()))
                .andExpect(status().isCreated());

        // Revoke → back to STUDENT.
        mvc.perform(post("/api/admin/experts/" + applicant.id() + "/revoke")
                        .with(bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"stale\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.verificationStatus").value("PENDING"));
        assertThat(roleOf(applicant)).isEqualTo(UserRole.STUDENT);
    }

    private UserRole roleOf(AuthedUser u) {
        return userRepository.findById(u.id()).orElseThrow().getRole();
    }
}
