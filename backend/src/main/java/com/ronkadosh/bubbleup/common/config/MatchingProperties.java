package com.ronkadosh.bubbleup.common.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

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
        BehaviorDeltas deltas
) {
    /**
     * Cold-start confidence knobs.
     * <ul>
     *   <li>{@code question_confidence = min(answered / questionCap, 1)}</li>
     *   <li>{@code behavior_confidence = min(events / behaviorCap, 1)}</li>
     *   <li>{@code user_confidence = min(questionWeight·qc + behaviorWeight·bc, userCap)}</li>
     *   <li>{@code group_profile_confidence = avg(member_confs) · min(member_count / groupMemberCap, 1)}</li>
     * </ul>
     */
    public record Confidence(
            double questionCap,
            double behaviorCap,
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

    public record BehaviorDeltas(
            double createdGroupLeader,
            double addedMemberLeader,
            double createdCalendarEventPlanner,
            double uploadedFileExpert,
            double sentMessageCommunicator
    ) {}
}
