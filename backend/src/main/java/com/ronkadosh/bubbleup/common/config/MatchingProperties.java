package com.ronkadosh.bubbleup.common.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "app.matching")
public record MatchingProperties(
        double credibilityThreshold,
        int quizQuestionCap,
        int recommendationLimit,
        int candidateLimitPerCourse,
        int weightShiftDayStart,
        int weightShiftDayEnd,
        double quizWeightMax,
        double quizWeightMin,
        Duration quizCooldown,
        RoleTargets targets,
        BehaviorDeltas deltas
) {
    public record RoleTargets(
            double leader,
            double planner,
            double expert,
            double creative,
            double communicator,
            double teamPlayer,
            double challenger
    ) {
        public double[] asArray() {
            return new double[]{leader, planner, expert, creative, communicator, teamPlayer, challenger};
        }
    }

    public record BehaviorDeltas(
            double createdGroupLeader,
            double addedMemberLeader,
            double createdCalendarEventPlanner,
            double uploadedFileExpert,
            double sentMessageCommunicator
    ) {}
}
