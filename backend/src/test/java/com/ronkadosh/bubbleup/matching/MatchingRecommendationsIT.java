package com.ronkadosh.bubbleup.matching;

import com.fasterxml.jackson.databind.JsonNode;
import com.ronkadosh.bubbleup.catalog.internal.CatalogInternalService;
import com.ronkadosh.bubbleup.catalog.model.Course;
import com.ronkadosh.bubbleup.catalog.persistence.CourseRepository;
import com.ronkadosh.bubbleup.groups.model.GroupMember;
import com.ronkadosh.bubbleup.groups.model.GroupVisibility;
import com.ronkadosh.bubbleup.groups.model.MembershipRole;
import com.ronkadosh.bubbleup.groups.model.StudyGroup;
import com.ronkadosh.bubbleup.groups.persistence.GroupMemberRepository;
import com.ronkadosh.bubbleup.groups.persistence.GroupRepository;
import com.ronkadosh.bubbleup.matching.application.MatchingCommandService;
import com.ronkadosh.bubbleup.matching.model.GroupProfile;
import com.ronkadosh.bubbleup.matching.model.UserProfile;
import com.ronkadosh.bubbleup.matching.persistence.GroupProfileRepository;
import com.ronkadosh.bubbleup.matching.persistence.UserProfileRepository;
import com.ronkadosh.bubbleup.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * End-to-end recommendation scenarios. Profiles are seeded directly (inert — no
 * matching events fired, no QuizResponse rows) so each case has exact vectors and
 * confidence; the {@code ?courseId=} path is used so candidate resolution doesn't
 * depend on enrollment or the current term. The live {@code computeLive} runs
 * synchronously inside the request, so no async wait is needed.
 *
 * <p>Each test claims its own catalog course (shared H2 persists across tests) to
 * avoid cross-test group bleed.
 */
class MatchingRecommendationsIT extends IntegrationTest {

    // Strong on Leader+Planner, weak elsewhere.
    private static final double[] LEADER_PLANNER = {0.9, 0.9, 0.1, 0.1, 0.1, 0.1, 0.1};
    private static final double[] ZERO = new double[7];

    private static final AtomicInteger COURSE_IDX = new AtomicInteger(0);

    @Autowired UserProfileRepository userProfileRepository;
    @Autowired GroupProfileRepository groupProfileRepository;
    @Autowired GroupRepository groupRepository;
    @Autowired GroupMemberRepository groupMemberRepository;
    @Autowired CourseRepository courseRepository;
    @Autowired CatalogInternalService catalogInternalService;
    @Autowired MatchingCommandService matchingCommandService;

    @Test
    void knownUser_complementaryGroup_isMatchedWithHighPercent() throws Exception {
        AuthedUser viewer = registerAndLogin();
        seedUserProfile(viewer.id(), LEADER_PLANNER, 7, 30);   // user_confidence = 1.0
        UUID courseId = claimCourse();
        candidate(courseId, viewer.id(), new double[]{0.1, 0.1, 0.8, 0.8, 0.8, 0.8, 0.8},
                0.9, 6, 20, 1, 1);

        JsonNode g = recommendFirst(viewer, courseId);
        assertThat(g.get("displayMode").asText()).isEqualTo("MATCHED");
        assertThat(g.get("matchPercent").asInt()).isGreaterThan(80);
    }

    @Test
    void knownUser_duplicateRoleGroup_isMatchedWithLowPercent() throws Exception {
        AuthedUser viewer = registerAndLogin();
        seedUserProfile(viewer.id(), LEADER_PLANNER, 7, 30);
        UUID courseId = claimCourse();
        candidate(courseId, viewer.id(), new double[]{0.8, 0.8, 0.1, 0.1, 0.1, 0.1, 0.1},
                0.9, 6, 8, 0, 0);

        JsonNode g = recommendFirst(viewer, courseId);
        assertThat(g.get("displayMode").asText()).isEqualTo("MATCHED");
        assertThat(g.get("matchPercent").asInt()).isLessThan(50);
    }

    @Test
    void coldUser_knownGroup_isTrendingWithReasonsAndNoPercent() throws Exception {
        AuthedUser viewer = registerAndLogin();
        seedUserProfile(viewer.id(), ZERO, 0, 0);   // user_confidence = 0 → matching_confidence 0
        UUID courseId = claimCourse();
        candidate(courseId, viewer.id(), new double[]{0.2, 0.2, 0.7, 0.7, 0.7, 0.7, 0.7},
                0.9, 8, 40, 2, 1);

        JsonNode g = recommendFirst(viewer, courseId);
        assertThat(g.get("displayMode").asText()).isEqualTo("TRENDING");
        assertThat(g.get("matchPercent").isNull()).isTrue();
        assertThat(g.get("reasonLabels")).isNotEmpty();
    }

    @Test
    void knownUser_lowConfidenceGroup_isTrending() throws Exception {
        AuthedUser viewer = registerAndLogin();
        seedUserProfile(viewer.id(), LEADER_PLANNER, 7, 30);   // user_confidence 1.0
        UUID courseId = claimCourse();
        // group confidence 0.1 → matching_confidence 0.1 < 0.30 threshold → TRENDING
        candidate(courseId, viewer.id(), new double[]{0.5, 0.5, 0.5, 0.5, 0.5, 0.5, 0.5},
                0.1, 2, 30, 3, 2);

        JsonNode g = recommendFirst(viewer, courseId);
        assertThat(g.get("displayMode").asText()).isEqualTo("TRENDING");
        assertThat(g.get("matchPercent").isNull()).isTrue();
    }

    @Test
    void trending_smallActiveBubble_ranksAboveLargeDeadBubble() throws Exception {
        AuthedUser viewer = registerAndLogin();
        seedUserProfile(viewer.id(), ZERO, 0, 0);              // cold user → pure trending order
        UUID courseId = claimCourse();
        candidate(courseId, viewer.id(), uniform(0.4), 0.0, /*members*/9, 0, 0, 0);   // big & quiet
        candidate(courseId, viewer.id(), uniform(0.4), 0.0, /*members*/2, 60, 5, 5);  // small & buzzing

        JsonNode groups = recommend(viewer, courseId);
        assertThat(groups).hasSize(2);
        // The buzzing bubble (memberCount 2) must rank first despite being smaller.
        assertThat(groups.get(0).get("memberCount").asInt()).isEqualTo(2);
    }

    @Test
    void groupAverage_isConfidenceWeighted_soStrongKnownMemberDominates() throws Exception {
        UUID courseId = claimCourse();
        UUID offeringId = anyOfferingForCourse(courseId);
        StudyGroup group = groupRepository.save(StudyGroup.builder()
                .name("F bubble").description("x").visibility(GroupVisibility.PUBLIC)
                .offeringId(offeringId).maxMembers(10).createdBy(UUID.randomUUID()).build());

        // 1 well-known Expert (conf 1.0) vs 4 barely-known Communicators (conf ~0.1).
        addMember(group.getId(), seedMember(new double[]{0, 0, 1, 0, 0, 0, 0}, 7, 30));
        for (int i = 0; i < 4; i++) {
            addMember(group.getId(), seedMember(new double[]{0, 0, 0, 0, 1, 0, 0}, 1, 0));
        }

        matchingCommandService.doRecomputeGroupProfile(group.getId());

        GroupProfile gp = groupProfileRepository.findByGroupId(group.getId()).orElseThrow();
        // Expert (index 2) should dominate Communicator (index 4) thanks to confidence weighting.
        assertThat(gp.getAvgScore(2)).isGreaterThan(gp.getAvgScore(4));
        assertThat(gp.getMemberCount()).isEqualTo(5);
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private JsonNode recommend(AuthedUser u, UUID courseId) throws Exception {
        String json = mvc.perform(get("/api/matching/recommendations")
                        .param("courseId", courseId.toString())
                        .with(bearer(u)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return om.readTree(json).get("data").get("groups");
    }

    private JsonNode recommendFirst(AuthedUser u, UUID courseId) throws Exception {
        JsonNode groups = recommend(u, courseId);
        assertThat(groups).isNotEmpty();
        return groups.get(0);
    }

    private void seedUserProfile(UUID userId, double[] vector, int answered, int behaviorEvents) {
        UserProfile p = UserProfile.builder()
                .userId(userId)
                .answeredQuestions(answered)
                .meaningfulBehaviorEvents(behaviorEvents)
                .updatedAt(Instant.now())
                .build();
        for (int r = 0; r < 7; r++) p.setQuizScore(r, vector[r]);
        userProfileRepository.save(p);
    }

    private UUID seedMember(double[] vector, int answered, int behaviorEvents) {
        UUID id = UUID.randomUUID();
        seedUserProfile(id, vector, answered, behaviorEvents);
        return id;
    }

    private void addMember(UUID groupId, UUID userId) {
        groupMemberRepository.save(GroupMember.builder()
                .groupId(groupId).userId(userId).role(MembershipRole.MEMBER).build());
    }

    private UUID candidate(UUID courseId, UUID createdBy, double[] avg, double groupConfidence,
                           int memberCount, long activity, int recentJoins, int upcoming) {
        UUID offeringId = anyOfferingForCourse(courseId);
        StudyGroup group = groupRepository.save(StudyGroup.builder()
                .name("IT bubble " + UUID.randomUUID())
                .description("scenario")
                .visibility(GroupVisibility.PUBLIC)
                .offeringId(offeringId)
                .maxMembers(10)
                .createdBy(createdBy)
                .build());
        GroupProfile gp = GroupProfile.builder()
                .groupId(group.getId())
                .memberCount(memberCount)
                .groupProfileConfidence(groupConfidence)
                .trendingActivityCount(activity)
                .trendingRecentJoins(recentJoins)
                .trendingUpcomingSessions(upcoming)
                .updatedAt(Instant.now())
                .build();
        for (int r = 0; r < 7; r++) gp.setAvgScore(r, avg[r]);
        groupProfileRepository.save(gp);
        return group.getId();
    }

    private UUID anyOfferingForCourse(UUID courseId) {
        List<UUID> offerings = catalogInternalService.offeringIdsForCourses(List.of(courseId));
        assertThat(offerings).as("seeded course must have an offering").isNotEmpty();
        return offerings.get(0);
    }

    /** A distinct seeded course per test, so groups created here don't bleed across tests. */
    private UUID claimCourse() {
        List<Course> all = courseRepository.findAll();
        return all.get(COURSE_IDX.getAndIncrement() % all.size()).getId();
    }

    private static double[] uniform(double v) {
        double[] a = new double[7];
        for (int i = 0; i < 7; i++) a[i] = v;
        return a;
    }
}
