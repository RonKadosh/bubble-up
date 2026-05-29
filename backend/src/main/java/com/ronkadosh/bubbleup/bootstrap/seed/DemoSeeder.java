package com.ronkadosh.bubbleup.bootstrap.seed;

import com.ronkadosh.bubbleup.auth.model.User;
import com.ronkadosh.bubbleup.auth.persistence.UserRepository;
import com.ronkadosh.bubbleup.catalog.model.Course;
import com.ronkadosh.bubbleup.catalog.model.Department;
import com.ronkadosh.bubbleup.catalog.model.University;
import com.ronkadosh.bubbleup.catalog.persistence.CourseRepository;
import com.ronkadosh.bubbleup.catalog.persistence.DepartmentRepository;
import com.ronkadosh.bubbleup.catalog.persistence.UniversityRepository;
import com.ronkadosh.bubbleup.common.context.UserRole;
import com.ronkadosh.bubbleup.enrollment.application.EnrollmentCommandService;
import com.ronkadosh.bubbleup.groups.api.dto.CreateGroupRequest;
import com.ronkadosh.bubbleup.groups.application.GroupCommandService;
import com.ronkadosh.bubbleup.groups.model.GroupVisibility;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;

/**
 * Boots a small demo population so the app is usable as soon as it starts:
 * 10 users split across 3 Study Bubbles. Idempotent — if {@code alice@bubble.up}
 * already exists we assume the seed already ran and skip everything.
 *
 * <p>Toggle with {@code app.demo.seed.enabled} (default ON for dev convenience;
 * turn OFF in any environment that should not have shared demo credentials).
 *
 * <p>Listens on {@link ApplicationReadyEvent} (rather than {@code ApplicationRunner})
 * so it fires AFTER every runner — including {@code CatalogSeeder} — without
 * needing fragile {@code @Order} coordination.
 */
@Component
@ConditionalOnProperty(name = "app.demo.seed.enabled", havingValue = "true", matchIfMissing = false)
@RequiredArgsConstructor
@Slf4j
public class DemoSeeder {

    private static final String DEMO_PASSWORD = "Passw0rd!";
    private static final String ANCHOR_EMAIL = "alice@bubble.up";

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final GroupCommandService groupCommandService;
    private final EnrollmentCommandService enrollmentCommandService;
    private final UniversityRepository universityRepository;
    private final DepartmentRepository departmentRepository;
    private final CourseRepository courseRepository;

    @EventListener(ApplicationReadyEvent.class)
    public void seed() {
        if (userRepository.existsByEmail(ANCHOR_EMAIL)) {
            log.info("DemoSeeder: anchor user {} already present — skipping", ANCHOR_EMAIL);
            return;
        }

        Optional<University> bgu = universityRepository.findByShortCode("BGU");
        if (bgu.isEmpty()) {
            log.warn("DemoSeeder: BGU university not present — catalog seed may have been disabled. Skipping demo seed.");
            return;
        }
        UUID universityId = bgu.get().getId();
        UUID departmentId = departmentRepository.findByUniversityIdAndShortCode(universityId, "ISE")
                .map(Department::getId)
                .orElse(null);

        // ── 10 users ────────────────────────────────────────────────────────
        UUID alice  = createUser("alice@bubble.up",  "Alice Anderson",   universityId, departmentId);
        UUID bob    = createUser("bob@bubble.up",    "Bob Brown",        universityId, departmentId);
        UUID carol  = createUser("carol@bubble.up",  "Carol Carter",     universityId, departmentId);
        UUID dave   = createUser("dave@bubble.up",   "Dave Davies",      universityId, departmentId);
        UUID eve    = createUser("eve@bubble.up",    "Eve Eastman",      universityId, departmentId);
        UUID frank  = createUser("frank@bubble.up",  "Frank Foster",     universityId, departmentId);
        UUID grace  = createUser("grace@bubble.up",  "Grace Goodman",    universityId, departmentId);
        UUID henry  = createUser("henry@bubble.up",  "Henry Hayes",      universityId, departmentId);
        UUID isla   = createUser("isla@bubble.up",   "Isla Ibrahim",     universityId, departmentId);
        UUID jack   = createUser("jack@bubble.up",   "Jack Jacobs",      universityId, departmentId);

        // ── 3 groups, each on a different course ────────────────────────────
        // Owner is the first listed user; others join via the public-group join flow.
        // Cross-membership (alice in #1 and #3, bob in #1 and #3) makes the demo
        // a bit more interesting for the relevance / dashboard surfaces.
        UUID osGroup = createGroupForCourse(
                universityId, "372.1.1117",
                "Operating Systems study Bubble",
                "Weekly OS practice — exam prep + past paper walkthroughs.",
                alice);
        addMembers(osGroup, bob, carol, dave);
        enrollAll(universityId, "372.1.1117", alice, bob, carol, dave);

        UUID mathGroup = createGroupForCourse(
                universityId, "214.1.9111",
                "Discrete Math Crew",
                "Problem sets every Sunday. Bring snacks.",
                eve);
        addMembers(mathGroup, frank, grace, henry);
        enrollAll(universityId, "214.1.9111", eve, frank, grace, henry);

        UUID dlGroup = createGroupForCourse(
                universityId, "237.2.6101",
                "Deep Learning Reading Group",
                "We pick one paper a week and dissect it together.",
                isla);
        addMembers(dlGroup, jack, alice, bob);
        enrollAll(universityId, "237.2.6101", isla, jack, alice, bob);

        // ── Admin user — for the /admin panel ──────────────────────────────
        createUserWithRole(
                "admin@bubble.up",
                "Demo Admin",
                UserRole.ADMIN,
                universityId,
                departmentId);

        log.info("DemoSeeder: seeded 10 demo users, 1 admin, and 3 Study Bubbles");
    }

    private UUID createUser(String email, String displayName, UUID universityId, UUID departmentId) {
        return createUserWithRole(email, displayName, UserRole.STUDENT, universityId, departmentId);
    }

    private UUID createUserWithRole(String email, String displayName, UserRole role,
                                    UUID universityId, UUID departmentId) {
        if (userRepository.existsByEmail(email)) {
            return userRepository.findByEmail(email).orElseThrow().getId();
        }
        User saved = userRepository.save(User.builder()
                .email(email)
                .passwordHash(passwordEncoder.encode(DEMO_PASSWORD))
                .role(role)
                .displayName(displayName)
                .universityId(universityId)
                .departmentId(departmentId)
                .enrollmentYear(2024)
                .build());
        return saved.getId();
    }

    private UUID createGroupForCourse(UUID universityId, String courseCode, String name,
                                      String description, UUID ownerId) {
        Course course = courseRepository.findByUniversityIdAndCode(universityId, courseCode)
                .orElseThrow(() -> new IllegalStateException(
                        "DemoSeeder: course " + courseCode + " not found — check catalog seed"));
        CreateGroupRequest req = new CreateGroupRequest(
                name,
                description,
                GroupVisibility.PUBLIC,
                /* offeringId */ null,
                /* courseId   */ course.getId());
        return groupCommandService.createGroup(req, ownerId).id();
    }

    private void enrollAll(UUID universityId, String courseCode, UUID... userIds) {
        Course course = courseRepository.findByUniversityIdAndCode(universityId, courseCode)
                .orElseThrow(() -> new IllegalStateException(
                        "DemoSeeder: course " + courseCode + " not found for enrollment seed"));
        for (UUID userId : userIds) {
            try {
                enrollmentCommandService.enroll(userId, course.getId());
            } catch (RuntimeException e) {
                // Idempotency: skip already-enrolled rows and any other seed-time hiccups
                // (e.g. no current term in a profile that disabled the catalog seed).
                log.warn("DemoSeeder: skip enroll {} → {}: {}", userId, courseCode, e.getMessage());
            }
        }
    }

    private void addMembers(UUID groupId, UUID... memberIds) {
        for (UUID memberId : memberIds) {
            try {
                groupCommandService.joinGroup(groupId, memberId);
            } catch (RuntimeException e) {
                log.warn("DemoSeeder: failed to add {} to group {}: {}", memberId, groupId, e.getMessage());
            }
        }
    }
}
