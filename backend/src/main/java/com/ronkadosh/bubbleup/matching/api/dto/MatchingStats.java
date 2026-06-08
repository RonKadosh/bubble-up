package com.ronkadosh.bubbleup.matching.api.dto;

import java.util.List;

/**
 * Aggregate, identity-free statistics for the admin Matching panel — the population
 * health of the character-profile system, so an admin can see whether matching is on
 * point and where to steer it. Intentionally contains <b>no individual user data</b>
 * (no per-user vectors, no identities) — only population aggregates and per-group
 * profiles (groups are public entities, and their profile is itself an aggregate).
 *
 * @param coverage          headline counts: profiled users, matched-ready, avg confidence, etc.
 * @param dominantRoles     histogram — how many users lead with each role
 * @param populationProfile the population's average role vector (the overall "shape")
 * @param groupConfidence   how group-profile trust is distributed (high/medium/low)
 * @param topGroups         most active groups + their characteristic role
 */
public record MatchingStats(
        Coverage coverage,
        List<RoleCount> dominantRoles,
        List<RoleScore> populationProfile,
        List<ConfidenceBucket> groupConfidence,
        List<TopGroup> topGroups
) {
    public record Coverage(
            long profiledUsers,
            long matchedReadyUsers,
            double avgConfidence,
            long quizAnswers,
            long profiledGroups
    ) {}

    /** Role keys: LEADER, PLANNER, EXPERT, CREATIVE, COMMUNICATOR, TEAM_PLAYER, CHALLENGER. */
    public record RoleCount(String role, long count) {}

    public record RoleScore(String role, double avg) {}

    public record ConfidenceBucket(String label, long count) {}

    /** A group's name + how characteristic-confident it is + its dominant role ("NONE" if cold). */
    public record TopGroup(String name, int memberCount, double confidence, String dominantRole) {}
}
