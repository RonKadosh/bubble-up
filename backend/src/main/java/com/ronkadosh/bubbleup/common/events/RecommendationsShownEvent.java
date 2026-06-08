package com.ronkadosh.bubbleup.common.events;

import java.util.List;
import java.util.UUID;

/**
 * A set of recommendations was shown to a user (the discovery feed was served).
 * Published from {@code MatchingQueryService.getRecommendations} for the final
 * returned list; the matching-feedback module records one impression per item so the
 * shown→joined funnel can be measured. {@code displayMode} is "MATCHED" or "TRENDING";
 * {@code matchPercent} is null for TRENDING.
 */
public record RecommendationsShownEvent(UUID userId, UUID courseId, List<Shown> items) {

    public record Shown(UUID groupId, String displayMode, Integer matchPercent, double matchingConfidence) {}
}
