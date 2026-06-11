package com.ronkadosh.bubbleup.bootstrap.seed;

import com.ronkadosh.bubbleup.auth.model.User;
import com.ronkadosh.bubbleup.auth.persistence.UserRepository;
import com.ronkadosh.bubbleup.calendar.model.CalendarEvent;
import com.ronkadosh.bubbleup.calendar.model.CalendarEventType;
import com.ronkadosh.bubbleup.calendar.model.CalendarOwnerType;
import com.ronkadosh.bubbleup.catalog.internal.CatalogInternalService;
import com.ronkadosh.bubbleup.catalog.internal.dto.TermRef;
import com.ronkadosh.bubbleup.catalog.model.Course;
import com.ronkadosh.bubbleup.catalog.model.CourseDepartment;
import com.ronkadosh.bubbleup.catalog.model.CourseOffering;
import com.ronkadosh.bubbleup.catalog.model.Department;
import com.ronkadosh.bubbleup.catalog.model.University;
import com.ronkadosh.bubbleup.catalog.persistence.CourseDepartmentRepository;
import com.ronkadosh.bubbleup.catalog.persistence.CourseOfferingRepository;
import com.ronkadosh.bubbleup.catalog.persistence.CourseRepository;
import com.ronkadosh.bubbleup.catalog.persistence.DepartmentRepository;
import com.ronkadosh.bubbleup.catalog.persistence.UniversityRepository;
import com.ronkadosh.bubbleup.chat.model.ChatLinkTargetType;
import com.ronkadosh.bubbleup.chat.model.ChatMessage;
import com.ronkadosh.bubbleup.chat.model.ChatMessageType;
import com.ronkadosh.bubbleup.chat.model.ChatPoll;
import com.ronkadosh.bubbleup.chat.model.ChatPollOption;
import com.ronkadosh.bubbleup.chat.model.ChatPollVote;
import com.ronkadosh.bubbleup.chat.model.ChatRoom;
import com.ronkadosh.bubbleup.common.config.LoadTestProperties;
import com.ronkadosh.bubbleup.common.context.UserRole;
import com.ronkadosh.bubbleup.enrollment.model.Enrollment;
import com.ronkadosh.bubbleup.groups.model.GroupMember;
import com.ronkadosh.bubbleup.groups.model.GroupVisibility;
import com.ronkadosh.bubbleup.groups.model.MembershipRole;
import com.ronkadosh.bubbleup.groups.model.StudyGroup;
import com.ronkadosh.bubbleup.matching.application.MatchingCommandService;
import com.ronkadosh.bubbleup.matching.model.GroupProfile;
import com.ronkadosh.bubbleup.matching.model.UserProfile;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

/**
 * Seeds a full-semester load-test dataset: ~10k users, ~500 courses+offerings,
 * ~40k enrollments, ~5k groups (each with a "general" room + matching profile),
 * ~100k chat messages (skewed; one forced hot group), polls/pins/calendar events,
 * and — as a final phase — the real per-user match-cache rebuild.
 *
 * <p><b>Inert direct writes, deliberately.</b> Rows go straight through the
 * {@link EntityManager} (the same seed-time module-boundary exception DemoSeeder
 * and MatchingScenarioSeeder already use) so no behavior/matching events fire and
 * a quarter-million rows insert in minutes. Every timestamp is set explicitly,
 * backdated across the current term's window — the entities' {@code @PrePersist}
 * hooks only fill timestamps when null.
 *
 * <p><b>Idempotency:</b> anchored on the first load user's email. A partially
 * failed run leaves the anchor present — wipe the DB before re-running.
 *
 * <p>Toggle with {@code app.loadtest.enabled} (default OFF). Volumes come from
 * {@link LoadTestProperties}; same knobs + same {@code randomSeed} = same dataset.
 */
@Component
@ConditionalOnProperty(name = "app.loadtest.enabled", havingValue = "true", matchIfMissing = false)
@RequiredArgsConstructor
@Slf4j
public class LoadTestSeeder {

    private static final String EMAIL_FMT = "loadtest-%d@bubble.up";
    private static final String PASSWORD = "Passw0rd!";

    private static final String[] GROUP_FLAVORS = {
            "Study Crew", "Exam Crammers", "Problem Set Club", "Deep Divers",
            "Office Hours Refugees", "Late Night Library", "Whiteboard Warriors", "Past Paper Posse"
    };
    private static final String[] PHRASES = {
            "Anyone started question 3 yet?",
            "I uploaded my notes from today's lecture.",
            "Can we move the session to 18:00?",
            "That proof finally clicked for me, happy to explain.",
            "The TA said this topic WILL be on the exam.",
            "Who's coming to the library tomorrow?",
            "I think the recurrence in part b is wrong in the slides.",
            "Found a great video that explains this — sharing the link in person.",
            "Let's split the past papers between us.",
            "Reminder: quiz next week covers chapters 4-6."
    };

    private final LoadTestProperties props;
    private final UserRepository userRepository;
    private final UniversityRepository universityRepository;
    private final DepartmentRepository departmentRepository;
    private final CourseRepository courseRepository;
    private final CourseOfferingRepository courseOfferingRepository;
    private final CourseDepartmentRepository courseDepartmentRepository;
    private final CatalogInternalService catalogInternalService;
    private final MatchingCommandService matchingCommandService;
    private final PasswordEncoder passwordEncoder;
    private final EntityManager em;
    private final PlatformTransactionManager txManager;

    // Built once in seed(), read by the phase methods.
    private TransactionTemplate tx;
    private Random rnd;
    private Instant termStart;
    private Instant activityEnd;     // min(now, term end) — no activity in the future
    private Instant termEnd;

    private final List<UUID> userIds = new ArrayList<>();
    private final Map<UUID, Instant> userCreated = new HashMap<>();
    private final Map<UUID, String> userName = new HashMap<>();
    private final Map<UUID, double[]> userQuiz = new HashMap<>();
    private final Map<UUID, Integer> userAnswered = new HashMap<>();
    private final List<UUID> offeringIds = new ArrayList<>();
    private final Map<UUID, List<UUID>> enrolledPool = new HashMap<>();   // offeringId → userIds

    @EventListener(ApplicationReadyEvent.class)
    public void seed() {
        String anchorEmail = EMAIL_FMT.formatted(0);
        if (userRepository.existsByEmail(anchorEmail)) {
            log.info("LoadTestSeeder: anchor {} already present — skipping", anchorEmail);
            return;
        }
        University bgu = universityRepository.findByShortCode("BGU").orElse(null);
        if (bgu == null) {
            log.warn("LoadTestSeeder: BGU not present — catalog seed disabled? Skipping.");
            return;
        }
        // The cache/discovery/feed paths all resolve the user's CURRENT-term
        // enrolments — seeding any other term would produce data the app never
        // surfaces. currentTermFor is the same resolver those paths use.
        TermRef term = catalogInternalService.currentTermFor(bgu.getId()).orElse(null);
        if (term == null) {
            log.error("LoadTestSeeder: no current/upcoming term for BGU — aborting (seed terms first).");
            return;
        }
        List<Department> departments = departmentRepository.findAllByUniversityId(bgu.getId());
        if (departments.isEmpty()) {
            log.error("LoadTestSeeder: no departments for BGU — aborting.");
            return;
        }

        tx = new TransactionTemplate(txManager);
        rnd = new Random(props.randomSeed());
        termStart = term.startsOn().atStartOfDay(ZoneOffset.UTC).toInstant();
        termEnd = term.endsOn().atStartOfDay(ZoneOffset.UTC).toInstant();
        Instant now = Instant.now();
        activityEnd = now.isBefore(termEnd) ? now : termEnd;
        if (!activityEnd.isAfter(termStart)) {
            // Upcoming-term edge (today before startsOn): spread across the whole term.
            activityEnd = termEnd;
        }

        long t0 = System.currentTimeMillis();
        log.info("LoadTestSeeder: seeding into term {} ({} → {}) — users={} courses={} groups/course={}",
                term.code(), term.startsOn(), term.endsOn(),
                props.users(), props.courses(), props.groupsPerCourse());

        seedCoursesAndOfferings(bgu.getId(), departments, term.id());
        seedUsersAndProfiles(bgu.getId(), departments);
        seedEnrollments();
        seedGroupsWithActivity();
        if (props.buildMatchCache()) {
            buildMatchCache();
        }

        log.info("LoadTestSeeder: done in {}s — {} users, {} offerings, {} groups/course",
                (System.currentTimeMillis() - t0) / 1000, userIds.size(), offeringIds.size(),
                props.groupsPerCourse());
    }

    // ── Phase 1: courses + dept links + offerings ───────────────────────────

    private void seedCoursesAndOfferings(UUID universityId, List<Department> departments, UUID termId) {
        long t0 = System.currentTimeMillis();
        tx.executeWithoutResult(s -> {
            for (int i = 0; i < props.courses(); i++) {
                String code = "LT-%04d".formatted(i + 1);
                // Find-or-create: tolerate a rerun after a partial catalog phase.
                Course course = courseRepository.findByUniversityIdAndCode(universityId, code)
                        .orElseGet(() -> {
                            Course c = Course.builder()
                                    .universityId(universityId)
                                    .code(code)
                                    .name("Load Course " + code)
                                    .creditPoints(BigDecimal.valueOf(2 + rnd.nextInt(4)))
                                    .description("Synthetic course for load testing.")
                                    .createdAt(termStart.minus(Duration.ofDays(30)))
                                    .build();
                            em.persist(c);
                            return c;
                        });
                Department dept = departments.get(i % departments.size());
                if (!courseDepartmentRepository.existsByCourseIdAndDepartmentId(course.getId(), dept.getId())) {
                    em.persist(CourseDepartment.builder()
                            .courseId(course.getId())
                            .departmentId(dept.getId())
                            .primary(true)
                            .createdAt(termStart.minus(Duration.ofDays(30)))
                            .build());
                }
                UUID offeringId = courseOfferingRepository.findByCourseIdAndTermId(course.getId(), termId)
                        .map(CourseOffering::getId)
                        .orElseGet(() -> {
                            CourseOffering o = CourseOffering.builder()
                                    .courseId(course.getId())
                                    .termId(termId)
                                    .createdAt(termStart.minus(Duration.ofDays(21)))
                                    .build();
                            em.persist(o);
                            return o.getId();
                        });
                offeringIds.add(offeringId);
                if ((i + 1) % 200 == 0) {
                    em.flush();
                    em.clear();
                }
            }
            em.flush();
            em.clear();
        });
        log.info("LoadTestSeeder: phase 1 — {} courses+offerings in {}ms",
                offeringIds.size(), System.currentTimeMillis() - t0);
    }

    // ── Phase 2: users + user_profiles ──────────────────────────────────────

    private void seedUsersAndProfiles(UUID universityId, List<Department> departments) {
        long t0 = System.currentTimeMillis();
        // BCrypt is intentionally slow — hash once, reuse for every load user.
        String hash = passwordEncoder.encode(PASSWORD);
        Instant signupWindowEnd = earlier(termStart.plus(Duration.ofDays(14)), activityEnd);

        int total = props.users();
        for (int from = 0; from < total; from += 1000) {
            int to = Math.min(from + 1000, total);
            int chunkFrom = from;
            tx.executeWithoutResult(s -> {
                for (int i = chunkFrom; i < to; i++) {
                    Instant createdAt = between(termStart, signupWindowEnd);
                    String displayName = "Load Student %05d".formatted(i);
                    User user = User.builder()
                            .email(EMAIL_FMT.formatted(i))
                            .passwordHash(hash)
                            .role(UserRole.STUDENT)
                            .displayName(displayName)
                            .universityId(universityId)
                            .departmentId(departments.get(i % departments.size()).getId())
                            .enrollmentYear(1 + rnd.nextInt(3))
                            .createdAt(createdAt)
                            .build();
                    em.persist(user);

                    double[] quiz = randomQuizShape();
                    int answered = 3 + rnd.nextInt(5);   // 3..7 — gives real user-confidence spread
                    UserProfile profile = UserProfile.builder()
                            .userId(user.getId())
                            .answeredQuestions(answered)
                            .updatedAt(createdAt)
                            .build();
                    for (int r = 0; r < 7; r++) profile.setQuizScore(r, quiz[r]);
                    em.persist(profile);

                    userIds.add(user.getId());
                    userCreated.put(user.getId(), createdAt);
                    userName.put(user.getId(), displayName);
                    userQuiz.put(user.getId(), quiz);
                    userAnswered.put(user.getId(), answered);
                    if ((i + 1) % 500 == 0) {
                        em.flush();
                        em.clear();
                    }
                }
                em.flush();
                em.clear();
            });
        }
        log.info("LoadTestSeeder: phase 2 — {} users+profiles in {}ms",
                userIds.size(), System.currentTimeMillis() - t0);
    }

    // ── Phase 3: enrollments (builds the per-offering pools) ────────────────

    private void seedEnrollments() {
        long t0 = System.currentTimeMillis();
        int perUser = Math.min(props.enrollmentsPerUser(), offeringIds.size());
        long[] count = {0};
        for (int from = 0; from < userIds.size(); from += 1000) {
            int to = Math.min(from + 1000, userIds.size());
            int chunkFrom = from;
            tx.executeWithoutResult(s -> {
                for (int i = chunkFrom; i < to; i++) {
                    UUID userId = userIds.get(i);
                    Instant signup = userCreated.get(userId);
                    // Distinct offerings per user — respects uk_enrollments_user_offering.
                    List<UUID> picks = pickDistinct(offeringIds, perUser);
                    for (UUID offeringId : picks) {
                        em.persist(Enrollment.builder()
                                .userId(userId)
                                .offeringId(offeringId)
                                .createdAt(signup.plus(Duration.ofMinutes(5 + rnd.nextInt(720))))
                                .build());
                        enrolledPool.computeIfAbsent(offeringId, k -> new ArrayList<>()).add(userId);
                        count[0]++;
                    }
                    if ((i + 1) % 500 == 0) {
                        em.flush();
                        em.clear();
                    }
                }
                em.flush();
                em.clear();
            });
        }
        log.info("LoadTestSeeder: phase 3 — {} enrollments in {}ms", count[0], System.currentTimeMillis() - t0);
    }

    // ── Phases 4-6 fused per offering: groups → rooms → members → profile → chat ──

    private void seedGroupsWithActivity() {
        long t0 = System.currentTimeMillis();
        long[] groups = {0};
        long[] messages = {0};
        boolean[] hotAssigned = {false};

        for (UUID offeringId : offeringIds) {
            List<UUID> pool = enrolledPool.getOrDefault(offeringId, List.of());
            if (pool.isEmpty()) continue;   // statistically unreachable at default volumes
            tx.executeWithoutResult(s -> {
                for (int j = 0; j < props.groupsPerCourse(); j++) {
                    boolean hot = !hotAssigned[0];
                    hotAssigned[0] = true;
                    messages[0] += seedOneGroup(offeringId, pool, j, hot);
                    groups[0]++;
                }
                em.flush();
                em.clear();
            });
        }
        log.info("LoadTestSeeder: phases 4-6 — {} groups, {} chat messages in {}ms",
                groups[0], messages[0], System.currentTimeMillis() - t0);
    }

    /** Creates one group with room, members, profile, and its full activity. Returns message count. */
    private int seedOneGroup(UUID offeringId, List<UUID> pool, int index, boolean hot) {
        UUID ownerId = pool.get(rnd.nextInt(pool.size()));
        Instant ownerSignup = userCreated.get(ownerId);
        Instant groupCreated = between(
                later(termStart.plus(Duration.ofDays(3)), ownerSignup.plus(Duration.ofHours(1))),
                midpoint(termStart, activityEnd));

        int memberTarget = Math.min(pool.size(), 4 + rnd.nextInt(7));   // 4..10, avg ≈ 7
        int maxMembers = Math.min(10, Math.max(4, memberTarget + rnd.nextInt(2)));

        StudyGroup group = StudyGroup.builder()
                .name(GROUP_FLAVORS[rnd.nextInt(GROUP_FLAVORS.length)] + " #" + (index + 1))
                .description("Synthetic load-test bubble.")
                .visibility(rnd.nextDouble() < props.publicFraction()
                        ? GroupVisibility.PUBLIC : GroupVisibility.PRIVATE)
                .offeringId(offeringId)
                .maxMembers(maxMembers)
                .createdBy(ownerId)
                .createdAt(groupCreated)
                .build();
        em.persist(group);

        ChatRoom room = ChatRoom.builder()
                .name("general")
                .groupId(group.getId())
                .createdAt(groupCreated)
                .build();
        em.persist(room);

        // Members: owner first, the rest distinct draws from the offering's enrolled pool.
        List<UUID> members = new ArrayList<>(memberTarget);
        members.add(ownerId);
        for (UUID candidate : pickDistinct(pool, memberTarget)) {
            if (members.size() >= memberTarget) break;
            if (!members.contains(candidate)) members.add(candidate);
        }
        Map<UUID, Instant> joinedAt = new HashMap<>();
        Instant joinWindowEnd = earlier(groupCreated.plus(Duration.ofDays(14)), activityEnd);
        for (int m = 0; m < members.size(); m++) {
            UUID userId = members.get(m);
            Instant joined = m == 0 ? groupCreated : between(groupCreated, joinWindowEnd);
            joinedAt.put(userId, joined);
            em.persist(GroupMember.builder()
                    .groupId(group.getId())
                    .userId(userId)
                    .role(m == 0 ? MembershipRole.OWNER : MembershipRole.MEMBER)
                    .joinedAt(joined)
                    .build());
        }

        em.persist(buildGroupProfile(group.getId(), members));

        return seedGroupChat(group, room.getId(), members, joinedAt, hot);
    }

    private GroupProfile buildGroupProfile(UUID groupId, List<UUID> members) {
        double[] avg = new double[7];
        double answeredSum = 0;
        for (UUID m : members) {
            double[] q = userQuiz.get(m);
            for (int r = 0; r < 7; r++) avg[r] += q[r];
            answeredSum += userAnswered.get(m);
        }
        for (int r = 0; r < 7; r++) avg[r] /= members.size();
        // Mirrors the shape of MatchingScorer.groupConfidence: avg member confidence
        // (approximated from answered questions) × size saturation.
        double confidence = (answeredSum / members.size() / 7.0) * 0.7
                * Math.min(members.size() / 5.0, 1.0);
        GroupProfile profile = GroupProfile.builder()
                .groupId(groupId)
                .memberCount(members.size())
                .groupProfileConfidence(confidence)
                .trendingRecentJoins(rnd.nextInt(3))
                .updatedAt(activityEnd)
                .build();
        for (int r = 0; r < 7; r++) profile.setAvgScore(r, avg[r]);
        return profile;
    }

    /** SYSTEM_JOINs + TEXT spread over the term + pins/poll/calendar extras. */
    private int seedGroupChat(StudyGroup group, UUID roomId, List<UUID> members,
                              Map<UUID, Instant> joinedAt, boolean hot) {
        int count = 0;
        Instant lastJoin = group.getCreatedAt();
        for (UUID m : members) {
            Instant joined = joinedAt.get(m);
            if (joined.isAfter(lastJoin)) lastJoin = joined;
            // Production stores the subject's display name as the content (see
            // ChatInternalServiceImpl.postSystemMessage); mirror it.
            em.persist(ChatMessage.builder()
                    .roomId(roomId)
                    .senderId(null)
                    .content(userName.get(m))
                    .messageType(ChatMessageType.SYSTEM_JOIN)
                    .subjectUserId(m)
                    .sentAt(joined)
                    .build());
            count++;
        }

        int budget = hot
                ? props.maxMessagesInOneGroup()
                : skewedBudget(props.avgMessagesPerGroup());
        int textCount = Math.max(0, budget - count);

        // Ascending walk from the last join to the end of activity, with jitter —
        // keeps (room_id, sent_at) realistic for the cursor-pagination index.
        List<ChatMessage> texts = new ArrayList<>(textCount);
        long windowSeconds = Math.max(3600, Duration.between(lastJoin, activityEnd).getSeconds());
        long step = Math.max(60, windowSeconds / Math.max(1, textCount));
        Instant cursor = lastJoin;
        for (int i = 0; i < textCount; i++) {
            cursor = cursor.plusSeconds((long) (step * (0.5 + rnd.nextDouble())));
            if (cursor.isAfter(activityEnd)) cursor = activityEnd;
            ChatMessage msg = ChatMessage.builder()
                    .roomId(roomId)
                    .senderId(members.get(rnd.nextInt(members.size())))
                    .content(PHRASES[rnd.nextInt(PHRASES.length)])
                    .messageType(ChatMessageType.TEXT)
                    .sentAt(cursor)
                    .build();
            em.persist(msg);
            texts.add(msg);
            count++;
        }

        if (!texts.isEmpty() && rnd.nextDouble() < props.pinnedFraction()) {
            int pins = 1 + rnd.nextInt(Math.min(3, texts.size()));
            for (ChatMessage pinned : pickDistinct(texts, pins)) {
                pinned.setPinned(true);
                pinned.setPinnedAt(earlier(pinned.getSentAt().plus(Duration.ofHours(2)), activityEnd));
                pinned.setPinnedByUserId(members.get(0));
            }
        }
        if (rnd.nextDouble() < props.pollFraction()) {
            count += seedPoll(roomId, members, lastJoin);
        }
        if (rnd.nextDouble() < props.calendarFraction()) {
            count += seedCalendarEvent(group.getId(), roomId, members.get(0), lastJoin);
        }
        return count;
    }

    /** LINK message → ChatPoll → options → votes. Returns messages added (1). */
    private int seedPoll(UUID roomId, List<UUID> members, Instant notBefore) {
        UUID creator = members.get(0);
        Instant at = between(notBefore, activityEnd);
        ChatMessage linkMessage = ChatMessage.builder()
                .roomId(roomId)
                .senderId(creator)
                .content("When should we meet?")
                .messageType(ChatMessageType.LINK)
                .linkTargetType(ChatLinkTargetType.POLL)
                .sentAt(at)
                .build();
        em.persist(linkMessage);

        // ChatPoll/ChatPollVote have NO @PrePersist — timestamps must be explicit.
        ChatPoll poll = ChatPoll.builder()
                .roomId(roomId)
                .messageId(linkMessage.getId())
                .question("When should we meet?")
                .allowMultiple(false)
                .createdByUserId(creator)
                .createdAt(at)
                .build();
        em.persist(poll);

        String[] optionTexts = {"Sunday 16:00", "Monday 18:00", "Wednesday 20:00"};
        List<ChatPollOption> options = new ArrayList<>(optionTexts.length);
        for (int p = 0; p < optionTexts.length; p++) {
            ChatPollOption option = ChatPollOption.builder()
                    .pollId(poll.getId())
                    .text(optionTexts[p])
                    .position(p)
                    .build();
            em.persist(option);
            options.add(option);
        }
        for (UUID voter : members) {
            if (rnd.nextDouble() < 0.7) {
                em.persist(ChatPollVote.builder()
                        .pollId(poll.getId())
                        .optionId(options.get(rnd.nextInt(options.size())).getId())
                        .userId(voter)
                        .votedAt(between(at, earlier(at.plus(Duration.ofDays(2)), activityEnd)))
                        .build());
            }
        }
        return 1;
    }

    /** CalendarEvent → LINK message surfacing it. Returns messages added (1). */
    private int seedCalendarEvent(UUID groupId, UUID roomId, UUID ownerId, Instant notBefore) {
        Instant at = between(notBefore, activityEnd);
        Instant startsAt = between(at, termEnd);
        CalendarEvent event = CalendarEvent.builder()
                .ownerType(CalendarOwnerType.GROUP)
                .ownerId(groupId)
                .createdBy(ownerId)
                .eventType(CalendarEventType.STUDY_SESSION)
                .description("Group study session")
                .startsAt(startsAt)
                .endsAt(startsAt.plus(Duration.ofHours(2)))
                .createdAt(at)
                .updatedAt(at)
                .build();
        em.persist(event);
        em.persist(ChatMessage.builder()
                .roomId(roomId)
                .senderId(ownerId)
                .content("Scheduled a study session")
                .messageType(ChatMessageType.LINK)
                .linkTargetType(ChatLinkTargetType.CALENDAR_EVENT)
                .linkTargetId(event.getId())
                .sentAt(at)
                .build());
        return 1;
    }

    // ── Phase 7: real per-user match-cache rebuild ──────────────────────────

    private void buildMatchCache() {
        long t0 = System.currentTimeMillis();
        int done = 0;
        int failed = 0;
        for (UUID userId : userIds) {
            try {
                matchingCommandService.refreshUserMatchCache(userId);
            } catch (RuntimeException e) {
                if (failed++ < 5) {
                    log.warn("LoadTestSeeder: cache refresh failed for {}: {}", userId, e.getMessage());
                }
            }
            if (++done % 1000 == 0) {
                log.info("LoadTestSeeder: phase 7 — match cache {}/{} users", done, userIds.size());
            }
        }
        log.info("LoadTestSeeder: phase 7 — match cache for {} users ({} failed) in {}s",
                done, failed, (System.currentTimeMillis() - t0) / 1000);
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    /** Random quiz role shape, max-normalized like the app's vectors. */
    private double[] randomQuizShape() {
        double[] v = new double[7];
        double max = 0;
        for (int r = 0; r < 7; r++) {
            v[r] = rnd.nextDouble();
            if (v[r] > max) max = v[r];
        }
        for (int r = 0; r < 7; r++) v[r] /= max;
        return v;
    }

    /** Lognormal-ish budget around the average; ~2% of groups run hot (×4). */
    private int skewedBudget(int avg) {
        double f = 0.3 + rnd.nextDouble() * 1.4;
        if (rnd.nextDouble() < 0.02) f *= 4;
        return Math.max(3, (int) Math.round(avg * f));
    }

    private <T> List<T> pickDistinct(List<T> from, int n) {
        if (n >= from.size()) return new ArrayList<>(from);
        List<T> copy = new ArrayList<>(from);
        java.util.Collections.shuffle(copy, rnd);
        return copy.subList(0, n);
    }

    private Instant between(Instant a, Instant b) {
        long s = a.getEpochSecond();
        long e = Math.max(s + 1, b.getEpochSecond());
        return Instant.ofEpochSecond(s + (long) (rnd.nextDouble() * (e - s)));
    }

    private static Instant earlier(Instant a, Instant b) {
        return a.isBefore(b) ? a : b;
    }

    private static Instant later(Instant a, Instant b) {
        return a.isAfter(b) ? a : b;
    }

    private static Instant midpoint(Instant a, Instant b) {
        return Instant.ofEpochSecond((a.getEpochSecond() + b.getEpochSecond()) / 2);
    }
}
