package com.ronkadosh.bubbleup.common.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Volume knobs for the full-semester load-test dataset seeded by
 * {@code bootstrap/seed/LoadTestSeeder}. OFF by default — turn on only against a
 * disposable database. The match-cache size is not a knob: it is derived by the
 * real {@code MatchingCommandService.refreshUserMatchCache} per user (Σ over users
 * of PUBLIC+ACTIVE non-member groups in their enrolled current-term offerings,
 * capped per offering by {@code app.matching.candidate-limit-per-course}).
 */
@ConfigurationProperties(prefix = "app.loadtest")
public record LoadTestProperties(
        boolean enabled,
        int users,
        int courses,
        int enrollmentsPerUser,
        int groupsPerCourse,
        int avgMembersPerGroup,
        int avgMessagesPerGroup,
        /** One group is forced to exactly this many messages (the "hot group" outlier). */
        int maxMessagesInOneGroup,
        /** Fraction of groups created PUBLIC (only PUBLIC+ACTIVE groups are matching candidates). */
        double publicFraction,
        /** Fraction of groups that get one poll (+options+votes). */
        double pollFraction,
        /** Fraction of groups that get a few pinned messages. */
        double pinnedFraction,
        /** Fraction of groups that get a study-session calendar event + LINK message. */
        double calendarFraction,
        /** When true, runs the real refreshUserMatchCache per user as the final phase. */
        boolean buildMatchCache,
        /** Fixed RNG seed — same knobs + same seed = same dataset. */
        long randomSeed
) {}
