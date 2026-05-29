package com.ronkadosh.bubbleup.groups;

import com.ronkadosh.bubbleup.catalog.persistence.DepartmentRepository;
import com.ronkadosh.bubbleup.catalog.persistence.UniversityRepository;
import com.ronkadosh.bubbleup.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class GroupsRelevantIT extends IntegrationTest {

    @Autowired private UniversityRepository universityRepository;
    @Autowired private DepartmentRepository departmentRepository;

    @Test
    void relevant_rejects_user_with_no_affiliation() throws Exception {
        AuthedUser u = registerAndLogin();
        mvc.perform(get("/api/groups/relevant").with(bearer(u)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("USER_AFFILIATION_REQUIRED"));
    }

    @Test
    void relevant_returns_groups_in_my_dept_after_setting_affiliation() throws Exception {
        AuthedUser u = registerAndLogin();
        UUID universityId = bguId();
        UUID departmentId = seDepartmentId(universityId);
        setAffiliation(u, universityId, departmentId);

        // Create a bubble against the seeded offering (SE department PLACEHOLDER-101 in 2025-B).
        UUID groupId = createGroup(u, seedOfferingId());

        mvc.perform(get("/api/groups/relevant").with(bearer(u)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[?(@.id == '" + groupId + "')]").exists());
    }

    @Test
    void relevant_with_termId_override_uses_that_term() throws Exception {
        AuthedUser u = registerAndLogin();
        UUID universityId = bguId();
        UUID departmentId = seDepartmentId(universityId);
        setAffiliation(u, universityId, departmentId);

        // Override with a clearly-not-a-real-term UUID — should yield empty list, not 400.
        mvc.perform(get("/api/groups/relevant")
                        .param("termId", UUID.randomUUID().toString())
                        .with(bearer(u)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(0));
    }

    private UUID createGroup(AuthedUser owner, UUID offeringId) throws Exception {
        String json = mvc.perform(post("/api/groups")
                        .with(bearer(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"g\",\"offeringId\":\"" + offeringId + "\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return UUID.fromString(om.readTree(json).get("data").get("id").asText());
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
