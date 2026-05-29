package com.ronkadosh.bubbleup.support;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ronkadosh.bubbleup.catalog.model.Course;
import com.ronkadosh.bubbleup.catalog.model.CourseOffering;
import com.ronkadosh.bubbleup.auth.internal.AuthInternalService;
import com.ronkadosh.bubbleup.catalog.persistence.CourseOfferingRepository;
import com.ronkadosh.bubbleup.catalog.persistence.CourseRepository;
import com.ronkadosh.bubbleup.catalog.persistence.DepartmentRepository;
import com.ronkadosh.bubbleup.catalog.persistence.UniversityRepository;
import com.ronkadosh.bubbleup.common.context.UserRole;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@ActiveProfiles("test")
public abstract class IntegrationTest {

    @Autowired protected MockMvc mvc;
    @Autowired protected ObjectMapper om;
    @Autowired private CourseRepository courseRepository;
    @Autowired private CourseOfferingRepository courseOfferingRepository;
    @Autowired private UniversityRepository universityRepository;
    @Autowired private DepartmentRepository departmentRepository;
    @Autowired private AuthInternalService authInternalService;

    /**
     * Any seeded course id. The catalog seeder runs at boot and produces a small
     * placeholder catalog (see {@code CatalogSeedConfig}), so this is always
     * available in test profile.
     */
    protected UUID seedCourseId() {
        return courseRepository.findAll().stream()
                .findFirst()
                .map(Course::getId)
                .orElseThrow(() -> new IllegalStateException(
                        "No seeded course — is CatalogSeeder enabled in the test profile?"));
    }

    /**
     * Any seeded offering id. Same source as {@link #seedCourseId()} — the seeder
     * creates offerings for the placeholder courses in the current term.
     */
    protected UUID seedOfferingId() {
        return courseOfferingRepository.findAll().stream()
                .findFirst()
                .map(CourseOffering::getId)
                .orElseThrow(() -> new IllegalStateException(
                        "No seeded offering — is CatalogSeeder enabled in the test profile?"));
    }

    /** Any seeded university id (the catalog seeder produces one — BGU in dev/test). */
    protected UUID seedUniversityId() {
        return universityRepository.findAll().stream()
                .findFirst()
                .map(u -> u.getId())
                .orElseThrow(() -> new IllegalStateException(
                        "No seeded university — is CatalogSeeder enabled in the test profile?"));
    }

    /** Any seeded department id (ISE in dev/test). */
    protected UUID seedDepartmentId() {
        return departmentRepository.findAll().stream()
                .findFirst()
                .map(d -> d.getId())
                .orElseThrow(() -> new IllegalStateException(
                        "No seeded department — is CatalogSeeder enabled in the test profile?"));
    }

    /**
     * Set affiliation (universityId + departmentId + enrollmentYear) on an
     * authed user via {@code PATCH /api/users/me/profile}. Required before any
     * enrollment flow — enroll() rejects users without affiliation.
     */
    protected void setAffiliation(AuthedUser u, UUID universityId, UUID departmentId) throws Exception {
        String body = String.format(
                "{\"universityId\":\"%s\",\"departmentId\":\"%s\",\"enrollmentYear\":1}",
                universityId, departmentId);
        mvc.perform(patch("/api/users/me/profile")
                        .with(bearer(u))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk());
    }

    /** Convenience: register + set affiliation to the seeded uni/dept in one call. */
    protected AuthedUser registerWithAffiliation() throws Exception {
        AuthedUser u = registerAndLogin();
        setAffiliation(u, seedUniversityId(), seedDepartmentId());
        return u;
    }

    /**
     * Promote a registered user to ADMIN by directly mutating the role. JWT
     * already in {@code AuthedUser.jwt} reflects the role at registration time
     * (STUDENT) — admin endpoints check the role via {@code @PreAuthorize} on
     * the JWT-derived authority, so call {@link #registerAndLoginAsAdmin()}
     * which re-logs to get a fresh JWT with the ADMIN claim.
     */
    protected void promoteToAdminInPlace(UUID userId) {
        authInternalService.changeRole(userId, UserRole.ADMIN);
    }

    /**
     * Registers a fresh user, promotes them to ADMIN, then re-logs them in so
     * the returned JWT carries the ADMIN role claim. Use this for any admin IT.
     */
    protected AuthedUser registerAndLoginAsAdmin() throws Exception {
        AuthedUser u = registerAndLogin();
        promoteToAdminInPlace(u.id());
        String body = String.format("{\"email\":\"%s\",\"password\":\"Passw0rd!\"}", u.email());
        String json = mvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        JsonNode data = om.readTree(json).get("data");
        return new AuthedUser(
                u.id(),
                u.email(),
                data.get("accessToken").asText(),
                data.get("refreshToken").asText()
        );
    }

    protected AuthedUser registerAndLogin() throws Exception {
        String email = "u" + UUID.randomUUID() + "@test.local";
        // Display name is required on the wire as of profile v1; tests don't care
        // about the value, just that the register call returns 201.
        String displayName = "Test " + email.substring(0, 9);
        String body = String.format(
                "{\"email\":\"%s\",\"password\":\"Passw0rd!\",\"displayName\":\"%s\"}",
                email, displayName);
        String json = mvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        JsonNode data = om.readTree(json).get("data");
        return new AuthedUser(
                UUID.fromString(data.get("userId").asText()),
                email,
                data.get("accessToken").asText(),
                data.get("refreshToken").asText()
        );
    }

    protected RequestPostProcessor bearer(AuthedUser u) {
        return req -> {
            req.addHeader("Authorization", "Bearer " + u.jwt());
            return req;
        };
    }

    /** `jwt` is the access JWT (kept short to minimize churn across existing tests). */
    public record AuthedUser(UUID id, String email, String jwt, String refreshToken) {}
}
