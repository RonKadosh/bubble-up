package com.ronkadosh.bubbleup.admin;

import com.fasterxml.jackson.databind.JsonNode;
import com.ronkadosh.bubbleup.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AdminCatalogControllerIT extends IntegrationTest {

    @Test
    void admin_can_create_university_then_block_delete_with_dependent() throws Exception {
        AuthedUser admin = registerAndLoginAsAdmin();

        // Create university.
        String uniBody = "{\"name\":\"Test U\",\"shortCode\":\"TST\",\"country\":\"US\"}";
        MvcResult uniRes = mvc.perform(post("/api/admin/catalog/universities")
                        .with(bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(uniBody))
                .andExpect(status().isCreated())
                .andReturn();
        JsonNode uni = om.readTree(uniRes.getResponse().getContentAsString()).get("data");
        UUID uniId = UUID.fromString(uni.get("id").asText());

        // Create department under it.
        String deptBody = "{\"name\":\"CS\",\"shortCode\":\"CS\"}";
        mvc.perform(post("/api/admin/catalog/universities/" + uniId + "/departments")
                        .with(bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(deptBody))
                .andExpect(status().isCreated());

        // Delete the university — rejected because dept exists.
        mvc.perform(delete("/api/admin/catalog/universities/" + uniId)
                        .with(bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"cleanup\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("ADMIN_CATALOG_UNIVERSITY_HAS_DEPENDENTS"));
    }

    @Test
    void term_with_invalid_dates_rejected() throws Exception {
        AuthedUser admin = registerAndLoginAsAdmin();
        String uniBody = "{\"name\":\"Test U2\",\"shortCode\":\"TS2\",\"country\":\"US\"}";
        MvcResult uniRes = mvc.perform(post("/api/admin/catalog/universities")
                        .with(bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(uniBody))
                .andExpect(status().isCreated())
                .andReturn();
        UUID uniId = UUID.fromString(
                om.readTree(uniRes.getResponse().getContentAsString()).get("data").get("id").asText());

        // endsOn before startsOn — rejected.
        String termBody = "{\"code\":\"FA26\",\"name\":\"Fall 2026\",\"kind\":\"FALL\","
                + "\"academicYear\":2026,\"startsOn\":\"2026-09-01\",\"endsOn\":\"2026-06-01\"}";
        mvc.perform(post("/api/admin/catalog/universities/" + uniId + "/terms")
                        .with(bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(termBody))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("ADMIN_CATALOG_TERM_DATES_INVALID"));
    }

    @Test
    void linking_course_to_department_from_different_university_rejected() throws Exception {
        AuthedUser admin = registerAndLoginAsAdmin();

        // Two universities.
        MvcResult u1Res = mvc.perform(post("/api/admin/catalog/universities")
                        .with(bearer(admin)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Uni A\",\"shortCode\":\"UA\",\"country\":\"US\"}"))
                .andExpect(status().isCreated()).andReturn();
        UUID u1 = UUID.fromString(om.readTree(u1Res.getResponse().getContentAsString())
                .get("data").get("id").asText());
        MvcResult u2Res = mvc.perform(post("/api/admin/catalog/universities")
                        .with(bearer(admin)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Uni B\",\"shortCode\":\"UB\",\"country\":\"US\"}"))
                .andExpect(status().isCreated()).andReturn();
        UUID u2 = UUID.fromString(om.readTree(u2Res.getResponse().getContentAsString())
                .get("data").get("id").asText());

        // Department under u1.
        MvcResult deptRes = mvc.perform(post("/api/admin/catalog/universities/" + u1 + "/departments")
                        .with(bearer(admin)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"CS\",\"shortCode\":\"CS\"}"))
                .andExpect(status().isCreated()).andReturn();
        UUID deptU1 = UUID.fromString(om.readTree(deptRes.getResponse().getContentAsString())
                .get("data").get("id").asText());

        // Course under u2.
        MvcResult courseRes = mvc.perform(post("/api/admin/catalog/courses")
                        .with(bearer(admin)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"universityId\":\"" + u2 + "\",\"code\":\"CS101\",\"name\":\"Intro\"}"))
                .andExpect(status().isCreated()).andReturn();
        UUID courseU2 = UUID.fromString(om.readTree(courseRes.getResponse().getContentAsString())
                .get("data").get("id").asText());

        // Try to link u2's course to u1's department.
        mvc.perform(post("/api/admin/catalog/courses/" + courseU2 + "/departments")
                        .with(bearer(admin)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"departmentId\":\"" + deptU1 + "\",\"primary\":true}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("DEPARTMENT_NOT_IN_UNIVERSITY"));
    }

    @Test
    void student_cannot_create_university() throws Exception {
        AuthedUser student = registerAndLogin();
        mvc.perform(post("/api/admin/catalog/universities")
                        .with(bearer(student))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"X\",\"shortCode\":\"X\",\"country\":\"US\"}"))
                .andExpect(status().isForbidden());
    }
}
