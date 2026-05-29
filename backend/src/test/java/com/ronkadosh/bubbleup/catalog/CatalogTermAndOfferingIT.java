package com.ronkadosh.bubbleup.catalog;

import com.fasterxml.jackson.databind.JsonNode;
import com.ronkadosh.bubbleup.catalog.persistence.UniversityRepository;
import com.ronkadosh.bubbleup.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class CatalogTermAndOfferingIT extends IntegrationTest {

    @Autowired private UniversityRepository universityRepository;

    @Test
    void list_terms_returns_seeded_terms_for_university() throws Exception {
        AuthedUser u = registerAndLogin();
        UUID universityId = bguId();
        mvc.perform(get("/api/catalog/universities/{id}/terms", universityId).with(bearer(u)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(3))
                .andExpect(jsonPath("$.data[0].code").value("2025-A"))
                .andExpect(jsonPath("$.data[1].code").value("2025-B"))
                .andExpect(jsonPath("$.data[2].code").value("2025-S"));
    }

    @Test
    void list_terms_for_unknown_university_returns_404() throws Exception {
        AuthedUser u = registerAndLogin();
        mvc.perform(get("/api/catalog/universities/{id}/terms", UUID.randomUUID()).with(bearer(u)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("UNIVERSITY_NOT_FOUND"));
    }

    @Test
    void current_term_endpoint_returns_a_seeded_term() throws Exception {
        AuthedUser u = registerAndLogin();
        UUID universityId = bguId();
        // SystemTimeProvider is @Primary in test profile too — we only assert the
        // shape (universityId echoes, code/dates are present). The exact term
        // depends on what "today" is when the test runs.
        mvc.perform(get("/api/catalog/universities/{id}/current-term", universityId).with(bearer(u)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.universityId").value(universityId.toString()))
                .andExpect(jsonPath("$.data.code").exists())
                .andExpect(jsonPath("$.data.startsOn").exists())
                .andExpect(jsonPath("$.data.endsOn").exists());
    }

    @Test
    void list_offerings_for_course_returns_seeded_offerings() throws Exception {
        AuthedUser u = registerAndLogin();
        UUID courseId = seedCourseId();
        String json = mvc.perform(get("/api/catalog/courses/{id}/offerings", courseId).with(bearer(u)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        JsonNode data = om.readTree(json).get("data");
        assert data.size() >= 1;
        assert data.get(0).get("courseId").asText().equals(courseId.toString());
    }

    @Test
    void get_offering_by_id_returns_expanded_view() throws Exception {
        AuthedUser u = registerAndLogin();
        UUID offeringId = seedOfferingId();
        mvc.perform(get("/api/catalog/offerings/{id}", offeringId).with(bearer(u)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(offeringId.toString()))
                .andExpect(jsonPath("$.data.courseId").exists())
                .andExpect(jsonPath("$.data.termId").exists())
                .andExpect(jsonPath("$.data.universityId").exists())
                .andExpect(jsonPath("$.data.courseCode").exists())
                .andExpect(jsonPath("$.data.termCode").exists());
    }

    @Test
    void get_offering_unknown_returns_404() throws Exception {
        AuthedUser u = registerAndLogin();
        mvc.perform(get("/api/catalog/offerings/{id}", UUID.randomUUID()).with(bearer(u)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("OFFERING_NOT_FOUND"));
    }

    private UUID bguId() {
        return universityRepository.findByShortCode("BGU")
                .orElseThrow()
                .getId();
    }
}
