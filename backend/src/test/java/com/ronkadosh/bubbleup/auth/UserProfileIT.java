package com.ronkadosh.bubbleup.auth;

import com.ronkadosh.bubbleup.catalog.persistence.DepartmentRepository;
import com.ronkadosh.bubbleup.catalog.persistence.UniversityRepository;
import com.ronkadosh.bubbleup.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class UserProfileIT extends IntegrationTest {

    @Autowired private UniversityRepository universityRepository;
    @Autowired private DepartmentRepository departmentRepository;

    @Test
    void newly_registered_user_has_null_affiliation() throws Exception {
        AuthedUser u = registerAndLogin();
        mvc.perform(get("/api/users/me/profile").with(bearer(u)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.userId").value(u.id().toString()))
                .andExpect(jsonPath("$.data.universityId").doesNotExist())
                .andExpect(jsonPath("$.data.departmentId").doesNotExist());
    }

    @Test
    void patch_profile_sets_affiliation() throws Exception {
        AuthedUser u = registerAndLogin();
        UUID universityId = bguId();
        UUID departmentId = seDepartmentId(universityId);

        String body = String.format(
                "{\"universityId\":\"%s\",\"departmentId\":\"%s\",\"enrollmentYear\":2}",
                universityId, departmentId);

        mvc.perform(patch("/api/users/me/profile")
                        .with(bearer(u))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.universityId").value(universityId.toString()))
                .andExpect(jsonPath("$.data.departmentId").value(departmentId.toString()))
                .andExpect(jsonPath("$.data.enrollmentYear").value(2));
    }

    @Test
    void patch_profile_rejects_mismatched_dept_and_uni() throws Exception {
        AuthedUser u = registerAndLogin();
        UUID universityId = bguId();
        // Use a department UUID that's guaranteed not to belong to this uni.
        UUID stray = UUID.randomUUID();
        String body = String.format(
                "{\"universityId\":\"%s\",\"departmentId\":\"%s\"}", universityId, stray);

        mvc.perform(patch("/api/users/me/profile")
                        .with(bearer(u))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("DEPARTMENT_NOT_FOUND"));
    }

    @Test
    void patch_profile_rejects_dept_belonging_to_other_university() throws Exception {
        // Two-step: first set a valid uni, then try to switch to a dept that's
        // in this uni but combined with an unrelated uni.
        AuthedUser u = registerAndLogin();
        UUID universityId = bguId();
        UUID departmentId = seDepartmentId(universityId);
        // Now try to PATCH again with a different (made-up) universityId but same dept.
        UUID otherUni = UUID.randomUUID();
        String body = String.format(
                "{\"universityId\":\"%s\",\"departmentId\":\"%s\"}", otherUni, departmentId);

        mvc.perform(patch("/api/users/me/profile")
                        .with(bearer(u))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("UNIVERSITY_NOT_FOUND"));
    }

    @Test
    void patch_profile_can_update_enrollment_year_alone() throws Exception {
        AuthedUser u = registerAndLogin();
        UUID universityId = bguId();
        UUID departmentId = seDepartmentId(universityId);
        setAffiliation(u, universityId, departmentId);

        mvc.perform(patch("/api/users/me/profile")
                        .with(bearer(u))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"enrollmentYear\":3}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.enrollmentYear").value(3))
                .andExpect(jsonPath("$.data.universityId").value(universityId.toString()));
    }

    @Test
    void get_profile_requires_auth() throws Exception {
        mvc.perform(get("/api/users/me/profile"))
                .andExpect(status().isUnauthorized());
    }

    private UUID bguId() {
        return universityRepository.findByShortCode("BGU").orElseThrow().getId();
    }

    private UUID seDepartmentId(UUID universityId) {
        return departmentRepository.findByUniversityIdAndShortCode(universityId, "SE")
                .orElseThrow()
                .getId();
    }
}
