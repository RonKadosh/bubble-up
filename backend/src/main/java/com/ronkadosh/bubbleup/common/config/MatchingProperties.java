package com.ronkadosh.bubbleup.common.config;

import com.ronkadosh.bubbleup.common.events.BehaviorEventType;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.Map;

@ConfigurationProperties(prefix = "app.matching")
public record MatchingProperties(
        int quizQuestionCap,
        int recommendationLimit,
        int candidateLimitPerCourse,
        int weightShiftDayStart,
        int weightShiftDayEnd,
        double quizWeightMax,
        double quizWeightMin,
        Duration quizCooldown,
        // matching_confidence at/above this → MATCHED (show %); below → TRENDING (reason labels).
        double matchedDisplayThreshold,
        Confidence confidence,
        Trending trending,
        // Per-action-type behavior signal definitions, keyed by event type. See BehaviorSignal.
        Map<BehaviorEventType, BehaviorSignal> signals
) {
    /**
     * Cold-start confidence knobs.
     * <ul>
     *   <li>{@code question_confidence = min(answered / questionCap, 1)}</li>
     *   <li>{@code behavior_confidence = min(behavior_evidence / behaviorEvidenceCap, 1)} —
     *       behavior_evidence = Σ over action types of {@code count/(count+k)} (saturated,
     *       diversity-aware; see {@code MatchingScorer.behaviorEvidence}). Volume of ONE
     *       action can't inflate it — only a spread of distinct actions does.</li>
     *   <li>{@code user_confidence = min(questionWeight·qc + behaviorWeight·bc, userCap)}</li>
     *   <li>{@code group_profile_confidence = avg(member_confs) · min(member_count / groupMemberCap, 1)}</li>
     * </ul>
     */
    public record Confidence(
            double questionCap,
            double behaviorEvidenceCap,
            double questionWeight,
            double behaviorWeight,
            double userCap,
            double groupMemberCap
    ) {}

    /**
     * Trending signal weights, windows, and normalization caps. Each raw signal is
     * normalized as {@code min(raw / cap, 1)} then weighted; weights should sum to 1.
     */
    public record Trending(
            double activityWeight,
            double recentJoinWeight,
            double memberCountWeight,
            double upcomingWeight,
            int activityWindowDays,
            int recentJoinWindowDays,
            int upcomingWindowDays,
            double activityCap,
            double recentJoinCap,
            double memberCountCap,
            double upcomingCap
    ) {}

    /**
     * One behavior signal's contribution rule.
     * <ul>
     *   <li>{@code k} — half-saturation count for the diminishing-returns curve
     *       {@code count/(count+k)}. Large k (chat: ~8) caps a frequent action's total
     *       contribution low so volume can't dominate the shape; small k (=1) lets one
     *       rare, high-intent act already signal strongly.</li>
     *   <li>{@code roles} — how much this action feeds each role, keyed by role name
     *       ({@code leader, planner, expert, creative, communicator, teamPlayer,
     *       challenger}). An action may feed two roles.</li>
     * </ul>
     */
    public record BehaviorSignal(
            double k,
            Map<String, Double> roles
    ) {}
}
