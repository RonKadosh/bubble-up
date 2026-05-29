package com.ronkadosh.bubbleup.enrollment;

import com.fasterxml.jackson.databind.JsonNode;
import com.ronkadosh.bubbleup.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class EnrollmentControllerIT extends IntegrationTest {

    @Test
    void enroll_happy_path() throws Exception {
        AuthedUser me = registerWithAffiliation();
        UUID courseId = seedCourseId();
        String body = String.format("{\"courseId\":\"%s\"}", courseId);
        mvc.perform(post("/api/enrollments")
                        .with(bearer(me))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.courseId").value(courseId.toString()))
                .andExpect(jsonPath("$.data.courseCode").exists())
                .andExpect(jsonPath("$.data.termId").exists());
    }

    @Test
    void enroll_twice_rejected() throws Exception {
        AuthedUser me = registerWithAffiliation();
        UUID courseId = seedCourseId();
        String body = String.format("{\"courseId\":\"%s\"}", courseId);
        mvc.perform(post("/api/enrollments")
                        .with(bearer(me))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated());
        mvc.perform(post("/api/enrollments")
                        .with(bearer(me))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("ENROLLMENT_ALREADY_EXISTS"));
    }

    @Test
    void enroll_without_affiliation_rejected() throws Exception {
        AuthedUser me = registerAndLogin();
        UUID courseId = seedCourseId();
        String body = String.format("{\"courseId\":\"%s\"}", courseId);
        mvc.perform(post("/api/enrollments")
                        .with(bearer(me))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("USER_AFFILIATION_REQUIRED"));
    }

    @Test
    void list_mine_after_enroll() throws Exception {
        AuthedUser me = registerWithAffiliation();
        UUID courseId = seedCourseId();
        mvc.perform(post("/api/enrollments")
                        .with(bearer(me))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(String.format("{\"courseId\":\"%s\"}", courseId)))
                .andExpect(status().isCreated());
        mvc.perform(get("/api/enrollments/me").with(bearer(me)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data[0].courseId").value(courseId.toString()));
        mvc.perform(get("/api/enrollments/me/current").with(bearer(me)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].courseId").value(courseId.toString()));
    }

    @Test
    void unenroll_happy_path() throws Exception {
        AuthedUser me = registerWithAffiliation();
        UUID courseId = seedCourseId();
        MvcResult enrolled = mvc.perform(post("/api/enrollments")
                        .with(bearer(me))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(String.format("{\"courseId\":\"%s\"}", courseId)))
                .andExpect(status().isCreated())
                .andReturn();
        JsonNode data = om.readTree(enrolled.getResponse().getContentAsString()).get("data");
        UUID enrollmentId = UUID.fromString(data.get("id").asText());

        mvc.perform(delete("/api/enrollments/" + enrollmentId).with(bearer(me)))
                .andExpect(status().isOk());

        mvc.perform(get("/api/enrollments/me").with(bearer(me)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(0));
    }

    @Test
    void unenroll_someone_elses_returns_not_found() throws Exception {
        AuthedUser me = registerWithAffiliation();
        AuthedUser other = registerWithAffiliation();
        UUID courseId = seedCourseId();
        MvcResult enrolled = mvc.perform(post("/api/enrollments")
                        .with(bearer(me))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(String.format("{\"courseId\":\"%s\"}", courseId)))
                .andExpect(status().isCreated())
                .andReturn();
        JsonNode data = om.readTree(enrolled.getResponse().getContentAsString()).get("data");
        UUID enrollmentId = UUID.fromString(data.get("id").asText());
        mvc.perform(delete("/api/enrollments/" + enrollmentId).with(bearer(other)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("ENROLLMENT_NOT_FOUND"));
    }
}
