package com.ronkadosh.bubbleup.groups;

import com.ronkadosh.bubbleup.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class GroupsByCourseIT extends IntegrationTest {

    @Test
    void not_enrolled_is_rejected() throws Exception {
        AuthedUser me = registerWithAffiliation();
        UUID courseId = seedCourseId();
        mvc.perform(get("/api/groups/by-course/" + courseId).with(bearer(me)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("NOT_ENROLLED_IN_COURSE"));
    }

    @Test
    void enrolled_sees_groups_under_offering() throws Exception {
        AuthedUser me = registerWithAffiliation();
        UUID courseId = seedCourseId();

        // Create a group under this course (auto-attaches to current offering) so
        // the list is non-empty.
        String create = String.format(
                "{\"name\":\"Test Group\",\"description\":\"\",\"visibility\":\"PUBLIC\",\"courseId\":\"%s\"}",
                courseId);
        mvc.perform(post("/api/groups")
                        .with(bearer(me))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(create))
                .andExpect(status().isCreated());

        // Enroll.
        mvc.perform(post("/api/enrollments")
                        .with(bearer(me))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(String.format("{\"courseId\":\"%s\"}", courseId)))
                .andExpect(status().isCreated());

        // List by course succeeds and includes the group we just created.
        // (Other tests in this class share the H2 DB; tolerate prior artifacts.)
        mvc.perform(get("/api/groups/by-course/" + courseId).with(bearer(me)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[?(@.name == 'Test Group')]").exists());
    }

    @Test
    void admin_bypasses_enrollment_gate() throws Exception {
        AuthedUser admin = registerAndLoginAsAdmin();
        UUID courseId = seedCourseId();
        // Admin doesn't need affiliation? They do — getGroupsByCourse reads
        // universityId from caller's profile. Set it.
        setAffiliation(admin, seedUniversityId(), seedDepartmentId());
        mvc.perform(get("/api/groups/by-course/" + courseId).with(bearer(admin)))
                .andExpect(status().isOk());
    }

    @Test
    void search_filter_works() throws Exception {
        AuthedUser me = registerWithAffiliation();
        UUID courseId = seedCourseId();
        // Create two groups with different names.
        mvc.perform(post("/api/groups")
                        .with(bearer(me))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(String.format(
                                "{\"name\":\"Alpha\",\"description\":\"\",\"visibility\":\"PUBLIC\",\"courseId\":\"%s\"}",
                                courseId)))
                .andExpect(status().isCreated());
        mvc.perform(post("/api/groups")
                        .with(bearer(me))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(String.format(
                                "{\"name\":\"Beta\",\"description\":\"\",\"visibility\":\"PUBLIC\",\"courseId\":\"%s\"}",
                                courseId)))
                .andExpect(status().isCreated());
        mvc.perform(post("/api/enrollments")
                        .with(bearer(me))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(String.format("{\"courseId\":\"%s\"}", courseId)))
                .andExpect(status().isCreated());

        mvc.perform(get("/api/groups/by-course/" + courseId + "?q=alph").with(bearer(me)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].name").value("Alpha"));
    }
}
