package com.ronkadosh.bubbleup.matching.application;

import com.ronkadosh.bubbleup.auth.model.User;
import com.ronkadosh.bubbleup.auth.persistence.UserRepository;
import com.ronkadosh.bubbleup.catalog.internal.CatalogInternalService;
import com.ronkadosh.bubbleup.catalog.internal.dto.TermRef;
import com.ronkadosh.bubbleup.catalog.model.Course;
import com.ronkadosh.bubbleup.catalog.model.Department;
import com.ronkadosh.bubbleup.catalog.model.University;
import com.ronkadosh.bubbleup.catalog.persistence.CourseRepository;
import com.ronkadosh.bubbleup.catalog.persistence.DepartmentRepository;
import com.ronkadosh.bubbleup.catalog.persistence.UniversityRepository;
import com.ronkadosh.bubbleup.common.context.UserRole;
import com.ronkadosh.bubbleup.common.datetime.TimeProvider;
import com.ronkadosh.bubbleup.enrollment.application.EnrollmentCommandService;
import com.ronkadosh.bubbleup.groups.model.GroupVisibility;
import com.ronkadosh.bubbleup.groups.model.StudyGroup;
import com.ronkadosh.bubbleup.groups.persistence.GroupRepository;
import com.ronkadosh.bubbleup.matching.model.GroupProfile;
import com.ronkadosh.bubbleup.matching.model.UserProfile;
import com.ronkadosh.bubbleup.matching.persistence.GroupProfileRepository;
import com.ronkadosh.bubbleup.matching.persistence.UserProfileRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * Seeds end-to-end matching/recommendation scenarios so every cold-start case from
 * the v1 design can be eyeballed in a browser by logging in as the scenario user
 * and opening the dashboard (Discovery section). Toggle with
 * {@code app.matching.scenario.seed.enabled} (default OFF). Idempotent on the
 * scenario-A anchor user.
 *
 * <p><b>Why direct repository writes.</b> The scenarios need exact user vectors,
 * exact group profiles, and exact confidence/trending values. Routing through the
 * normal services would (a) fire {@code UserBehaviorEvent}/{@code GroupMembershipChangedEvent},
 * whose async handlers would <em>recompute</em> the very profiles we hand-set, and
 * (b) rebuild the user quiz vector from {@code QuizResponse} rows (which we don't
 * create), zeroing it. So scenario users/groups are <b>inert</b>: we persist
 * {@link UserProfile} and {@link GroupProfile} rows directly and never fire a
 * matching event for them. The recommendation read path only <em>reads</em>
 * profiles, so the seeded values survive. Enrollment ({@link EnrollmentCommandService})
 * fires no event, so it's safe — and needed, since discovery is enrollment-driven.
 *
 * <p>Each scenario uses its own catalog course so a viewer only sees that scenario's
 * bubbles. Note: candidate groups get a {@link GroupProfile} (with a hand-set
 * {@code memberCount}) but no {@code GroupMember} rows — fine because nothing
 * recomputes them and the viewer is never a member.
 *
 * <p>Scenario F (confidence-weighted group-average dominance) is exercised by the
 * {@code MatchingRecommendationsIT} recompute path rather than here, since it's a
 * statement about the averaging math, not a dashboard view.
 */
@Component
@ConditionalOnProperty(name = "app.matching.scenario.seed.enabled", havingValue = "true", matchIfMissing = false)
@RequiredArgsConstructor
@Slf4j
public class MatchingScenarioSeeder {

    private static final String PASSWORD = "Passw0rd!";
    private static final String ANCHOR_EMAIL = "scenario-a@bubble.up";

    // Distinct catalog courses (none used by DemoSeeder) so scenarios don't bleed together.
    private static final String COURSE_A = "372.1.2104";
    private static final String COURSE_B = "372.1.2306";
    private static final String COURSE_C = "372.1.2402";
    private static final String COURSE_D = "372.1.3021";
    private static final String COURSE_E = "372.1.3401";
    private static final String COURSE_G = "237.1.1021";

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final UserProfileRepository userProfileRepository;
    private final GroupProfileRepository groupProfileRepository;
    private final GroupRepository groupRepository;
    private final UniversityRepository universityRepository;
    private final DepartmentRepository departmentRepository;
    private final CourseRepository courseRepository;
    private final CatalogInternalService catalogInternalService;
    private final EnrollmentCommandService enrollmentCommandService;
    private final TimeProvider timeProvider;

    @EventListener(ApplicationReadyEvent.class)
    public void seed() {
        if (userRepository.existsByEmail(ANCHOR_EMAIL)) {
            log.info("MatchingScenarioSeeder: anchor {} present — skipping", ANCHOR_EMAIL);
            return;
        }
        Optional<University> bgu = universityRepository.findByShortCode("BGU");
        if (bgu.isEmpty()) {
            log.warn("MatchingScenarioSeeder: BGU not present — catalog seed disabled? skipping");
            return;
        }
        UUID uniId = bgu.get().getId();
        UUID deptId = departmentRepository.findByUniversityIdAndShortCode(uniId, "ISE")
                .map(Department::getId).orElse(null);
        UUID hostId = createUser("scenario-host@bubble.up", "Scenario Host", uniId, deptId);

        // ── A: unknown user + unknown (low-confidence) group → TRENDING ──────
        UUID a = viewer("scenario-a@bubble.up", "Scenario A (cold/cold)", uniId, deptId,
                COURSE_A, zeros(), 0, 0);
        candidate(uniId, COURSE_A, "Intro OOP Study Circle", hostId,
                vec(0.3, 0.3, 0.3, 0.3, 0.3, 0.3, 0.3), 0.10, /*members*/3,
                /*activity*/12, /*joins*/2, /*upcoming*/1);
        log.info("MatchingScenarioSeeder: A viewer={} (expect TRENDING, no %)", a);

        // ── B: known user + complementary known group → high MATCHED % ───────
        viewer("scenario-b@bubble.up", "Scenario B (complementary)", uniId, deptId,
                COURSE_B, vec(0.9, 0.9, 0.1, 0.1, 0.1, 0.1, 0.1), 7, 30);
        candidate(uniId, COURSE_B, "Computational Models — needs leaders", hostId,
                vec(0.1, 0.1, 0.8, 0.8, 0.8, 0.8, 0.8), 0.90, 6, 20, 1, 1);

        // ── C: known user + duplicate-role group → low MATCHED % ─────────────
        viewer("scenario-c@bubble.up", "Scenario C (duplicate)", uniId, deptId,
                COURSE_C, vec(0.9, 0.9, 0.1, 0.1, 0.1, 0.1, 0.1), 7, 30);
        candidate(uniId, COURSE_C, "Web Dev — already all leaders", hostId,
                vec(0.8, 0.8, 0.1, 0.1, 0.1, 0.1, 0.1), 0.90, 6, 8, 0, 0);

        // ── D: known user + low-confidence group → TRENDING ──────────────────
        viewer("scenario-d@bubble.up", "Scenario D (untrusted group)", uniId, deptId,
                COURSE_D, vec(0.9, 0.9, 0.1, 0.1, 0.1, 0.1, 0.1), 7, 30);
        candidate(uniId, COURSE_D, "Regression Lab (members barely known)", hostId,
                vec(0.5, 0.5, 0.5, 0.5, 0.5, 0.5, 0.5), 0.10, 2, 30, 3, 2);

        // ── E: unknown user + known group → TRENDING ─────────────────────────
        viewer("scenario-e@bubble.up", "Scenario E (cold user)", uniId, deptId,
                COURSE_E, zeros(), 0, 0);
        candidate(uniId, COURSE_E, "Systems Analysis — well established", hostId,
                vec(0.2, 0.2, 0.7, 0.7, 0.7, 0.7, 0.7), 0.90, 8, 40, 2, 1);

        // ── G: trending variety — large-but-dead vs small-but-active ─────────
        viewer("scenario-g@bubble.up", "Scenario G (trending order)", uniId, deptId,
                COURSE_G, zeros(), 0, 0);
        candidate(uniId, COURSE_G, "Big Quiet Probability Group", hostId,
                vec(0.4, 0.4, 0.4, 0.4, 0.4, 0.4, 0.4), 0.0, /*members*/9, 0, 0, 0);
        candidate(uniId, COURSE_G, "Small Buzzing Probability Group", hostId,
                vec(0.4, 0.4, 0.4, 0.4, 0.4, 0.4, 0.4), 0.0, /*members*/2, 60, 5, 5);

        log.info("MatchingScenarioSeeder: seeded scenarios A–E + G");
    }

    /** Creates the viewer user + their exact UserProfile, and enrolls them in the scenario course. */
    private UUID viewer(String email, String name, UUID uniId, UUID deptId,
                        String courseCode, double[] quizVector, int answered, int behaviorEvents) {
        UUID userId = createUser(email, name, uniId, deptId);
        Instant now = timeProvider.now();
        UserProfile profile = UserProfile.builder()
                .userId(userId)
                .answeredQuestions(answered)
                .meaningfulBehaviorEvents(behaviorEvents)
                .updatedAt(now)
                .build();
        for (int r = 0; r < 7; r++) profile.setQuizScore(r, quizVector[r]);
        userProfileRepository.save(profile);
        enroll(userId, uniId, courseCode);
        return userId;
    }

    /** Creates a PUBLIC bubble on the course's current-term offering + its exact GroupProfile. */
    private void candidate(UUID uniId, String courseCode, String name, UUID ownerId,
                           double[] avg, double groupConfidence, int memberCount,
                           long activityCount, int recentJoins, int upcomingSessions) {
        UUID offeringId = currentOffering(uniId, courseCode);
        if (offeringId == null) {
            log.warn("MatchingScenarioSeeder: no current offering for {} — skipping group {}", courseCode, name);
            return;
        }
        StudyGroup group = groupRepository.save(StudyGroup.builder()
                .name(name)
                .description("Matching scenario bubble")
                .visibility(GroupVisibility.PUBLIC)
                .offeringId(offeringId)
                .maxMembers(10)
                .createdBy(ownerId)
                .build());
        Instant now = timeProvider.now();
        GroupProfile gp = GroupProfile.builder()
                .groupId(group.getId())
                .memberCount(memberCount)
                .groupProfileConfidence(groupConfidence)
                .trendingActivityCount(activityCount)
                .trendingRecentJoins(recentJoins)
                .trendingUpcomingSessions(upcomingSessions)
                .updatedAt(now)
                .build();
        for (int r = 0; r < 7; r++) gp.setAvgScore(r, avg[r]);
        groupProfileRepository.save(gp);
    }

    private UUID createUser(String email, String displayName, UUID uniId, UUID deptId) {
        return userRepository.findByEmail(email).map(User::getId).orElseGet(() ->
                userRepository.save(User.builder()
                        .email(email)
                        .passwordHash(passwordEncoder.encode(PASSWORD))
                        .role(UserRole.STUDENT)
                        .displayName(displayName)
                        .universityId(uniId)
                        .departmentId(deptId)
                        .enrollmentYear(2024)
                        .build()).getId());
    }

    private void enroll(UUID userId, UUID uniId, String courseCode) {
        Course course = courseRepository.findByUniversityIdAndCode(uniId, courseCode).orElse(null);
        if (course == null) return;
        try {
            enrollmentCommandService.enroll(userId, course.getId());
        } catch (RuntimeException e) {
            log.warn("MatchingScenarioSeeder: enroll {} → {} skipped: {}", userId, courseCode, e.getMessage());
        }
    }

    private UUID currentOffering(UUID uniId, String courseCode) {
        Course course = courseRepository.findByUniversityIdAndCode(uniId, courseCode).orElse(null);
        if (course == null) return null;
        return catalogInternalService.currentTermFor(uniId)
                .map(TermRef::id)
                .flatMap(termId -> catalogInternalService.offeringIdForCourseAndTerm(course.getId(), termId))
                .orElse(null);
    }

    private static double[] zeros() {
        return new double[7];
    }

    private static double[] vec(double l, double p, double e, double cr, double co, double tp, double ch) {
        return new double[]{l, p, e, cr, co, tp, ch};
    }
}
