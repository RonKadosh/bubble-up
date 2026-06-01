package com.ronkadosh.bubbleup.matching;

import com.ronkadosh.bubbleup.common.config.MatchingProperties;
import com.ronkadosh.bubbleup.matching.application.MatchingScorer;
import com.ronkadosh.bubbleup.matching.application.MatchingWeights;
import com.ronkadosh.bubbleup.matching.model.GroupProfile;
import com.ronkadosh.bubbleup.matching.model.MatchResultType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
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
                new MatchingProperties.Confidence(7, 30, 0.70, 0.30, 1.0, 5),
                new MatchingProperties.Trending(0.35, 0.30, 0.20, 0.15, 14, 14, 30, 50, 5, 10, 5),
                new MatchingProperties.BehaviorDeltas(0.10, 0.06, 0.08, 0.07, 0.04)
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
        assertThat(MatchingScorer.userConfidence(0, 30, props)).isCloseTo(0.30, within(1e-9));
    }

    @Test
    void userConfidence_bothSaturated_isOne() {
        assertThat(MatchingScorer.userConfidence(7, 30, props)).isCloseTo(1.0, within(1e-9));
        assertThat(MatchingScorer.userConfidence(14, 60, props)).isCloseTo(1.0, within(1e-9));
    }

    @Test
    void userConfidence_partialQuestions_isProportional() {
        assertThat(MatchingScorer.userConfidence(3, 0, props)).isCloseTo(0.70 * 3 / 7, within(1e-9));
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
