package com.ronkadosh.bubbleup.matching;

import com.ronkadosh.bubbleup.common.config.MatchingProperties;
import com.ronkadosh.bubbleup.matching.application.MatchingScorer;
import com.ronkadosh.bubbleup.matching.application.MatchingWeights;
import com.ronkadosh.bubbleup.matching.model.GroupProfile;
import com.ronkadosh.bubbleup.matching.model.UserProfile;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class MatchingScorerTest {

    private MatchingProperties props;

    @BeforeEach
    void setup() {
        props = new MatchingProperties(
                0.25,   // credibilityThreshold
                12,     // quizQuestionCap
                5,      // recommendationLimit
                200,    // candidateLimitPerCourse
                5,      // weightShiftDayStart
                14,     // weightShiftDayEnd
                0.70,   // quizWeightMax
                0.20,   // quizWeightMin
                Duration.ofMinutes(10),   // quizCooldown
                new MatchingProperties.RoleTargets(0.55, 0.60, 0.60, 0.50, 0.70, 0.70, 0.45),
                new MatchingProperties.BehaviorDeltas(0.10, 0.06, 0.08, 0.07, 0.04)
        );
    }

    @Test
    void quizWeight_atDayZero_returnsMax() {
        assertThat(MatchingWeights.quizWeight(0, props)).isEqualTo(0.70);
    }

    @Test
    void quizWeight_atWeightShiftDayStart_returnsMax() {
        assertThat(MatchingWeights.quizWeight(5, props)).isEqualTo(0.70);
    }

    @Test
    void quizWeight_atMidpoint_returnsInterpolated() {
        // day 9 is midpoint between 5 and 14; range=9, delta=0.50; step=0.50*(4/9) ≈ 0.222
        double expected = 0.70 - (0.70 - 0.20) * (9 - 5.0) / (14 - 5.0);
        assertThat(MatchingWeights.quizWeight(9, props)).isCloseTo(expected, within(1e-9));
    }

    @Test
    void quizWeight_atWeightShiftDayEnd_returnsMin() {
        assertThat(MatchingWeights.quizWeight(14, props)).isEqualTo(0.20);
    }

    @Test
    void quizWeight_beyondDayEnd_returnsMin() {
        assertThat(MatchingWeights.quizWeight(100, props)).isEqualTo(0.20);
    }

    @Test
    void matchPercent_allZeroScores_reliability0_returns50() {
        UserProfile user = UserProfile.builder().userId(UUID.randomUUID()).build();
        GroupProfile group = GroupProfile.builder().groupId(UUID.randomUUID()).build();
        double[] userFinal = MatchingScorer.userFinalScores(user, 0, props);
        int result = MatchingScorer.matchPercent(userFinal, group, 0.0, props);
        assertThat(result).isEqualTo(50);
    }

    @Test
    void matchPercent_groupFullyAtTargets_returns50_regardlessOfUser() {
        UserProfile user = UserProfile.builder().userId(UUID.randomUUID())
                .quizLeaderScore(1.0).build();
        // Set all avg scores to exactly target values — no gaps, totalNeed=0, raw=0.5
        GroupProfile group = GroupProfile.builder().groupId(UUID.randomUUID())
                .avgLeaderScore(0.55)
                .avgPlannerScore(0.60)
                .avgExpertScore(0.60)
                .avgCreativeScore(0.50)
                .avgCommunicatorScore(0.70)
                .avgTeamPlayerScore(0.70)
                .avgChallengerScore(0.45)
                .build();
        double[] userFinal = MatchingScorer.userFinalScores(user, 0, props);
        int result = MatchingScorer.matchPercent(userFinal, group, 1.0, props);
        assertThat(result).isEqualTo(50);
    }

    @Test
    void matchPercent_userStrongInLeader_groupNeedsLeader_returnsAbove50() {
        UserProfile user = UserProfile.builder().userId(UUID.randomUUID())
                .quizLeaderScore(1.0).build();
        // Group only lacks Leader — all other roles already at their targets (no gap)
        GroupProfile group = GroupProfile.builder().groupId(UUID.randomUUID())
                .avgLeaderScore(0.0)
                .avgPlannerScore(0.60)
                .avgExpertScore(0.60)
                .avgCreativeScore(0.50)
                .avgCommunicatorScore(0.70)
                .avgTeamPlayerScore(0.70)
                .avgChallengerScore(0.45)
                .build();
        double[] userFinal = MatchingScorer.userFinalScores(user, 0, props);
        int result = MatchingScorer.matchPercent(userFinal, group, 1.0, props);
        assertThat(result).isGreaterThan(50);
    }

    @Test
    void matchPercent_userWeakInLeader_groupNeedsLeader_returnsBelow50() {
        UserProfile user = UserProfile.builder().userId(UUID.randomUUID())
                .quizLeaderScore(0.0).build();
        // Group only lacks Leader — user cannot fill that need
        GroupProfile group = GroupProfile.builder().groupId(UUID.randomUUID())
                .avgLeaderScore(0.0)
                .avgPlannerScore(0.60)
                .avgExpertScore(0.60)
                .avgCreativeScore(0.50)
                .avgCommunicatorScore(0.70)
                .avgTeamPlayerScore(0.70)
                .avgChallengerScore(0.45)
                .build();
        double[] userFinal = MatchingScorer.userFinalScores(user, 0, props);
        int result = MatchingScorer.matchPercent(userFinal, group, 1.0, props);
        assertThat(result).isLessThan(50);
    }

    @Test
    void matchPercent_reliabilityPullsScoreToward50() {
        // User strong in Leader, group only needs Leader → raw score > 0.5
        // reliability 0.5 should pull it closer to 50 than reliability 1.0
        UserProfile user = UserProfile.builder().userId(UUID.randomUUID())
                .quizLeaderScore(1.0).build();
        GroupProfile group = GroupProfile.builder().groupId(UUID.randomUUID())
                .avgLeaderScore(0.0)
                .avgPlannerScore(0.60)
                .avgExpertScore(0.60)
                .avgCreativeScore(0.50)
                .avgCommunicatorScore(0.70)
                .avgTeamPlayerScore(0.70)
                .avgChallengerScore(0.45)
                .build();
        double[] userFinal = MatchingScorer.userFinalScores(user, 0, props);
        int fullResult   = MatchingScorer.matchPercent(userFinal, group, 1.0, props);
        int pulledResult = MatchingScorer.matchPercent(userFinal, group, 0.5, props);
        // pulled score should be between 50 and full score
        assertThat(pulledResult).isGreaterThan(50).isLessThan(fullResult);
    }
}
