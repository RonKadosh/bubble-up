package com.ronkadosh.bubbleup.catalog;

import com.fasterxml.jackson.databind.JsonNode;
import com.ronkadosh.bubbleup.catalog.model.Course;
import com.ronkadosh.bubbleup.catalog.model.CourseOffering;
import com.ronkadosh.bubbleup.catalog.model.Term;
import com.ronkadosh.bubbleup.catalog.persistence.CourseOfferingRepository;
import com.ronkadosh.bubbleup.catalog.persistence.CourseRepository;
import com.ronkadosh.bubbleup.catalog.persistence.TermRepository;
import com.ronkadosh.bubbleup.catalog.persistence.UniversityRepository;
import com.ronkadosh.bubbleup.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class CatalogCourseSearchIT extends IntegrationTest {

    @Autowired private UniversityRepository universityRepository;
    @Autowired private CourseRepository courseRepository;
    @Autowired private TermRepository termRepository;
    @Autowired private CourseOfferingRepository courseOfferingRepository;

    @Test
    void search_in_offered_term_returns_matching_course() throws Exception {
        AuthedUser u = registerAndLogin();
        UUID universityId = bguId();
        // Pick a real offering: its course is guaranteed offered in its term.
        CourseOffering offering = courseOfferingRepository.findAll().stream().findFirst().orElseThrow();
        Course seed = courseRepository.findById(offering.getCourseId()).orElseThrow();
        String code = seed.getCode();
        String q = code.substring(0, Math.min(3, code.length())).toLowerCase(Locale.ROOT);

        String json = mvc.perform(get("/api/catalog/courses/search")
                        .param("universityId", universityId.toString())
                        .param("termId", offering.getTermId().toString())
                        .param("q", q)
                        .with(bearer(u)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        JsonNode data = om.readTree(json).get("data");
        assert data.isArray();
        boolean found = false;
        for (JsonNode row : data) {
            if (row.get("id").asText().equals(seed.getId().toString())) found = true;
        }
        assert found : "expected seeded course " + seed.getCode() + " in search results for its offered term";
    }

    @Test
    void search_in_term_without_offering_hides_existing_course() throws Exception {
        AuthedUser u = registerAndLogin();
        UUID universityId = bguId();
        CourseOffering offering = courseOfferingRepository.findAll().stream().findFirst().orElseThrow();
        Course seed = courseRepository.findById(offering.getCourseId()).orElseThrow();
        String code = seed.getCode();
        String q = code.substring(0, Math.min(3, code.length())).toLowerCase(Locale.ROOT);

        // A term in which this course has no offering — the course exists overall but must not show.
        Set<UUID> termsWithOffering = courseOfferingRepository.findAllByCourseId(seed.getId()).stream()
                .map(CourseOffering::getTermId)
                .collect(Collectors.toSet());
        UUID emptyTermId = termRepository.findAllByUniversityIdOrderByStartsOnAsc(universityId).stream()
                .map(Term::getId)
                .filter(id -> !termsWithOffering.contains(id))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("no term without an offering for the seed course"));

        String json = mvc.perform(get("/api/catalog/courses/search")
                        .param("universityId", universityId.toString())
                        .param("termId", emptyTermId.toString())
                        .param("q", q)
                        .with(bearer(u)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        JsonNode data = om.readTree(json).get("data");
        for (JsonNode row : data) {
            assert !row.get("id").asText().equals(seed.getId().toString())
                    : "course offered in another term must not appear when searching a term it is not offered in";
        }
    }

    @Test
    void search_for_unknown_university_returns_404() throws Exception {
        AuthedUser u = registerAndLogin();
        UUID termId = anyTermId();
        mvc.perform(get("/api/catalog/courses/search")
                        .param("universityId", UUID.randomUUID().toString())
                        .param("termId", termId.toString())
                        .param("q", "intro")
                        .with(bearer(u)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("UNIVERSITY_NOT_FOUND"));
    }

    @Test
    void search_for_unknown_term_returns_404() throws Exception {
        AuthedUser u = registerAndLogin();
        mvc.perform(get("/api/catalog/courses/search")
                        .param("universityId", bguId().toString())
                        .param("termId", UUID.randomUUID().toString())
                        .param("q", "intro")
                        .with(bearer(u)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("TERM_NOT_FOUND"));
    }

    private UUID bguId() {
        return universityRepository.findByShortCode("BGU").orElseThrow().getId();
    }

    private UUID anyTermId() {
        return termRepository.findAll().stream().findFirst().orElseThrow().getId();
    }
}
