package com.ronkadosh.bubbleup.matching;

import com.ronkadosh.bubbleup.common.config.MatchingProperties;
import com.ronkadosh.bubbleup.common.config.MatchingProperties.BehaviorSignal;
import com.ronkadosh.bubbleup.common.events.BehaviorEventType;
import com.ronkadosh.bubbleup.matching.application.MatchingScorer;
import com.ronkadosh.bubbleup.matching.application.MatchingWeights;
import com.ronkadosh.bubbleup.matching.model.GroupProfile;
import com.ronkadosh.bubbleup.matching.model.MatchResultType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class MatchingScorerTest {

    private MatchingProperties props;

    @BeforeEach
    void setup() {
        props = new MatchingProperties(
                12,     // quizQuestionCap
                5,      // recommendationLimit
                200,    // candidateLimitPerCourse
                5,      // weightShiftDayStart
                14,     // weightShiftDayEnd
                0.70,   // quizWeightMax
                0.20,   // quizWeightMin
                Duration.ofMinutes(10),                       // quizCooldown
                0.30,                                          // matchedDisplayThreshold
                true,                                          // asyncRecompute
                new MatchingProperties.Confidence(7, 3, 0.70, 0.30, 1.0, 5),
                new MatchingProperties.Trending(0.35, 0.30, 0.20, 0.15, 14, 14, 30, 50, 5, 10, 5),
                Map.of(
                        BehaviorEventType.CREATED_GROUP,          new BehaviorSignal(1, Map.of("leader", 1.0)),
                        BehaviorEventType.CREATED_CALENDAR_EVENT, new BehaviorSignal(2, Map.of("planner", 1.0)),
                        BehaviorEventType.UPLOADED_FILE,          new BehaviorSignal(3, Map.of("expert", 0.8)),
                        BehaviorEventType.SENT_MESSAGE,           new BehaviorSignal(8, Map.of("communicator", 0.4)),
                        BehaviorEventType.CREATED_POLL,           new BehaviorSignal(2, Map.of("leader", 0.5, "planner", 0.5))
                )
        );
    }

    // ── profile building (max-normalized shape, not raw tally) ────────────────

    @Test
    void maxNormalize_scalesTopRoleToOne_preservingRatios() {
        double[] out = MatchingScorer.maxNormalize(new double[]{0.30, 0.15, 0, 0, 0, 0, 0});
        assertThat(out[0]).isCloseTo(1.0, within(1e-9));
        assertThat(out[1]).isCloseTo(0.5, within(1e-9));
        assertThat(out[2]).isEqualTo(0.0);
    }

    @Test
    void maxNormalize_isScaleInvariant() {
        double[] small = MatchingScorer.maxNormalize(new double[]{0.3, 0.15, 0, 0, 0, 0, 0});
        double[] big = MatchingScorer.maxNormalize(new double[]{3.0, 1.5, 0, 0, 0, 0, 0});
        assertThat(big).containsExactly(small);
    }

    @Test
    void maxNormalize_allZero_staysZero() {
        assertThat(MatchingScorer.maxNormalize(new double[7])).containsExactly(new double[7]);
    }

    @Test
    void buildQuizVector_isCountInvariant_forRepeatedAnswers() {
        double[] leader = {0.15, 0.10, 0, 0, 0, 0, 0};
        double[] once = MatchingScorer.buildQuizVector(List.of(leader));
        double[] thrice = MatchingScorer.buildQuizVector(List.of(leader, leader, leader));
        // Answering the same way more times must NOT change the character shape.
        assertThat(thrice).containsExactly(once, within(1e-9));
    }

    @Test
    void buildQuizVector_dominantRoleStaysAtOne_asSecondaryAnswersAccumulate() {
        double[] leaderHeavy = {0.15, 0, 0, 0, 0, 0, 0};
        double[] communicator = {0, 0, 0, 0, 0.05, 0, 0};
        // Smaller Communicator answers must not overtake a clear Leader lean,
        // and Leader must remain pinned at 1.0 (no clamp flattening).
        double[] v = MatchingScorer.buildQuizVector(
                List.of(leaderHeavy, communicator, communicator));   // Leader 0.15 vs Comm 0.10
        assertThat(v[0]).isEqualTo(1.0);
        assertThat(v[4]).isLessThan(1.0).isGreaterThan(0.0);
    }

    @Test
    void buildQuizVector_empty_isAllZero() {
        assertThat(MatchingScorer.buildQuizVector(List.of())).containsExactly(new double[7]);
    }

    // ── behavior vector (per-action saturation → normalized shape) ────────────

    @Test
    void behaviorVector_frequentActionDoesNotDominateRareHighIntentOne() {
        // 200 messages + 1 group created: chat must NOT collapse the shape to Communicator.
        double[] v = MatchingScorer.behaviorVector(Map.of(
                BehaviorEventType.SENT_MESSAGE, 200,
                BehaviorEventType.CREATED_GROUP, 1), props);
        assertThat(v[0]).isEqualTo(1.0);                 // Leader is the dominant shape
        assertThat(v[4]).isLessThan(1.0).isGreaterThan(0.0);  // Communicator present but secondary
        assertThat(v[0]).isGreaterThan(v[4]);
    }

    @Test
    void behaviorVector_saturatesWithDiminishingReturns() {
        // Communicator contribution (relative to a fixed Leader anchor) grows sub-linearly
        // with message count — the 10× from 80→800 adds far less than the 10× from 8→80.
        double comm8   = MatchingScorer.behaviorVector(Map.of(
                BehaviorEventType.CREATED_GROUP, 1, BehaviorEventType.SENT_MESSAGE, 8), props)[4];
        double comm80  = MatchingScorer.behaviorVector(Map.of(
                BehaviorEventType.CREATED_GROUP, 1, BehaviorEventType.SENT_MESSAGE, 80), props)[4];
        double comm800 = MatchingScorer.behaviorVector(Map.of(
                BehaviorEventType.CREATED_GROUP, 1, BehaviorEventType.SENT_MESSAGE, 800), props)[4];
        assertThat(comm80 - comm8).isGreaterThan(comm800 - comm80);   // diminishing
        assertThat(comm800).isLessThan(1.0);                          // never overtakes Leader
    }

    @Test
    void behaviorVector_empty_isAllZero() {
        assertThat(MatchingScorer.behaviorVector(Map.of(), props)).containsExactly(new double[7]);
    }

    @Test
    void behaviorVector_unmappedEventType_isIgnored() {
        // COMPLETED_SHARED_TASK has no signal entry → contributes nothing.
        assertThat(MatchingScorer.behaviorVector(
                Map.of(BehaviorEventType.COMPLETED_SHARED_TASK, 5), props))
                .containsExactly(new double[7]);
    }

    @Test
    void behaviorVector_multiRoleAction_splitsAcrossRoles() {
        // CREATED_POLL feeds Leader and Planner equally; nothing else.
        double[] v = MatchingScorer.behaviorVector(Map.of(BehaviorEventType.CREATED_POLL, 3), props);
        assertThat(v[0]).isEqualTo(1.0);   // leader
        assertThat(v[1]).isEqualTo(1.0);   // planner (equal split → both top out)
        assertThat(v[2]).isEqualTo(0.0);   // expert untouched
    }

    // ── quiz weight (unchanged) ───────────────────────────────────────────────

    @Test
    void quizWeight_atDayZero_returnsMax() {
        assertThat(MatchingWeights.quizWeight(0, props)).isEqualTo(0.70);
    }

    @Test
    void quizWeight_atMidpoint_returnsInterpolated() {
        double expected = 0.70 - (0.70 - 0.20) * (9 - 5.0) / (14 - 5.0);
        assertThat(MatchingWeights.quizWeight(9, props)).isCloseTo(expected, within(1e-9));
    }

    @Test
    void quizWeight_beyondDayEnd_returnsMin() {
        assertThat(MatchingWeights.quizWeight(100, props)).isEqualTo(0.20);
    }

    // ── cosine complementarity ────────────────────────────────────────────────

    @Test
    void cosine_identicalDirection_isOne() {
        double[] v = {1, 0, 0, 0, 0, 0, 0};
        assertThat(MatchingScorer.cosineComplementarity(v, v)).isCloseTo(1.0, within(1e-9));
    }

    @Test
    void cosine_orthogonal_isZero() {
        double[] v0 = {1, 0, 0, 0, 0, 0, 0};
        double[] need = {0, 1, 0, 0, 0, 0, 0};
        assertThat(MatchingScorer.cosineComplementarity(v0, need)).isCloseTo(0.0, within(1e-9));
    }

    @Test
    void cosine_zeroUserVector_isNeutralHalf() {
        double[] zero = new double[7];
        double[] need = {1, 1, 0, 0, 0, 0, 0};
        assertThat(MatchingScorer.cosineComplementarity(zero, need)).isEqualTo(0.5);
    }

    @Test
    void cosine_zeroNeed_isNeutralHalf() {
        double[] v0 = {1, 0, 0, 0, 0, 0, 0};
        assertThat(MatchingScorer.cosineComplementarity(v0, new double[7])).isEqualTo(0.5);
    }

    @Test
    void cosine_mixed_isExpected() {
        double[] v0 = {1, 0, 0, 0, 0, 0, 0};
        double[] need = {0.5, 0.5, 0, 0, 0, 0, 0};   // 0.5 / (1 * sqrt(0.5)) = 0.70710678
        assertThat(MatchingScorer.cosineComplementarity(v0, need)).isCloseTo(0.70710678, within(1e-6));
    }

    // ── user confidence ───────────────────────────────────────────────────────

    @Test
    void userConfidence_questionsOnly_capsAtPointSeven() {
        assertThat(MatchingScorer.userConfidence(7, 0, props)).isCloseTo(0.70, within(1e-9));
    }

    @Test
    void userConfidence_behaviorOnly_isPointThree() {
        // behavior arg is now saturated EVIDENCE, not raw count; cap is 3.0.
        assertThat(MatchingScorer.userConfidence(0, 3.0, props)).isCloseTo(0.30, within(1e-9));
    }

    @Test
    void userConfidence_bothSaturated_isOne() {
        assertThat(MatchingScorer.userConfidence(7, 3.0, props)).isCloseTo(1.0, within(1e-9));
        assertThat(MatchingScorer.userConfidence(14, 6.0, props)).isCloseTo(1.0, within(1e-9));
    }

    @Test
    void userConfidence_partialQuestions_isProportional() {
        assertThat(MatchingScorer.userConfidence(3, 0, props)).isCloseTo(0.70 * 3 / 7, within(1e-9));
    }

    // ── behavior evidence (anti-spam: diversity, not volume) ──────────────────

    @Test
    void behaviorEvidence_singleSpammedAction_staysBelowOne() {
        double ev = MatchingScorer.behaviorEvidence(Map.of(BehaviorEventType.SENT_MESSAGE, 500), props);
        assertThat(ev).isLessThan(1.0);   // one action type can't exceed its saturation ceiling
    }

    @Test
    void behaviorEvidence_rewardsDiversityOverVolume() {
        double spam = MatchingScorer.behaviorEvidence(Map.of(BehaviorEventType.SENT_MESSAGE, 100), props);
        double diverse = MatchingScorer.behaviorEvidence(Map.of(
                BehaviorEventType.SENT_MESSAGE, 5,
                BehaviorEventType.CREATED_GROUP, 1,
                BehaviorEventType.CREATED_POLL, 2,
                BehaviorEventType.UPLOADED_FILE, 3), props);
        assertThat(diverse).isGreaterThan(spam);
    }

    @Test
    void userConfidence_messageSpamAlone_cannotUnlockMatched() {
        // 1 quiz answer + heavy chat spam must stay below the matched-display threshold.
        double ev = MatchingScorer.behaviorEvidence(Map.of(BehaviorEventType.SENT_MESSAGE, 200), props);
        double conf = MatchingScorer.userConfidence(1, ev, props);
        assertThat(conf).isLessThan(props.matchedDisplayThreshold());
    }

    // ── group confidence ──────────────────────────────────────────────────────

    @Test
    void groupConfidence_fullMembers_isOne() {
        double[] confs = {1, 1, 1, 1, 1};
        assertThat(MatchingScorer.groupConfidence(confs, 5, props)).isCloseTo(1.0, within(1e-9));
    }

    @Test
    void groupConfidence_belowSizeCap_isDamped() {
        double[] confs = {1, 1};
        assertThat(MatchingScorer.groupConfidence(confs, 2, props)).isCloseTo(0.4, within(1e-9));
    }

    @Test
    void groupConfidence_noProfiledMembers_isZero() {
        assertThat(MatchingScorer.groupConfidence(new double[0], 0, props)).isEqualTo(0.0);
    }

    // ── trending ──────────────────────────────────────────────────────────────

    @Test
    void trending_allSignalsAtCap_isOne() {
        assertThat(MatchingScorer.trendingScore(50, 5, 10, 5, props)).isCloseTo(1.0, within(1e-9));
    }

    @Test
    void trending_onlyActivityAtCap_isActivityWeight() {
        assertThat(MatchingScorer.trendingScore(50, 0, 0, 0, props)).isCloseTo(0.35, within(1e-9));
    }

    @Test
    void trending_smallActive_beatsLargeDead() {
        double dead = MatchingScorer.trendingScore(0, 0, 9, 0, props);     // big but quiet
        double active = MatchingScorer.trendingScore(60, 5, 2, 5, props);  // small but buzzing
        assertThat(active).isGreaterThan(dead);
    }

    // ── blend ─────────────────────────────────────────────────────────────────

    @Test
    void blend_fullConfidence_isPersonalized() {
        assertThat(MatchingScorer.blend(1.0, 0.8, 0.2)).isCloseTo(0.8, within(1e-9));
    }

    @Test
    void blend_zeroConfidence_isTrending() {
        assertThat(MatchingScorer.blend(0.0, 0.8, 0.2)).isCloseTo(0.2, within(1e-9));
    }

    @Test
    void blend_halfConfidence_isMidpoint() {
        assertThat(MatchingScorer.blend(0.5, 0.8, 0.4)).isCloseTo(0.6, within(1e-9));
    }

    // ── combined score → display mode ─────────────────────────────────────────

    @Test
    void score_trustedComplement_isMatchedWithPercent() {
        double[] v0 = {1, 0, 0, 0, 0, 0, 0};                  // need = 1 - 0 = all ones
        GroupProfile gp = GroupProfile.builder().groupId(UUID.randomUUID())
                .groupProfileConfidence(0.9).build();         // all avg = 0 by default
        MatchingScorer.Scored s = MatchingScorer.score(v0, 1.0, gp, props);
        assertThat(s.mode()).isEqualTo(MatchResultType.MATCHED);
        // cosine([1,0..],[1,1,1,1,1,1,1]) = 1/sqrt(7) = 0.37796 → 38
        assertThat(s.matchPercent()).isEqualTo(38);
        assertThat(s.matchingConfidence()).isCloseTo(0.9, within(1e-9));
    }

    @Test
    void score_coldUser_isTrendingNoPercent() {
        GroupProfile gp = GroupProfile.builder().groupId(UUID.randomUUID())
                .groupProfileConfidence(0.9)
                .trendingActivityCount(50).trendingRecentJoins(5)
                .memberCount(10).trendingUpcomingSessions(5)
                .build();
        MatchingScorer.Scored s = MatchingScorer.score(new double[7], 0.0, gp, props);
        assertThat(s.mode()).isEqualTo(MatchResultType.TRENDING);
        assertThat(s.matchPercent()).isNull();
        assertThat(s.matchingConfidence()).isEqualTo(0.0);
        assertThat(s.finalScore()).isCloseTo(1.0, within(1e-9));   // blend collapses to trending=1.0
    }
}
