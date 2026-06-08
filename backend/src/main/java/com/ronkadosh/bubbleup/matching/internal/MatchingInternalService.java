package com.ronkadosh.bubbleup.matching.internal;

import com.ronkadosh.bubbleup.matching.internal.dto.GroupRecommendation;
import com.ronkadosh.bubbleup.matching.internal.dto.MatchSnapshot;
import com.ronkadosh.bubbleup.matching.internal.dto.MatchingReliability;

import java.util.List;
import java.util.UUID;

/**
 * Cross-module surface for the matching module.
 */
public interface MatchingInternalService {

    /**
     * Recommended groups for a user (matched if the user's quiz reliability is high
     * enough, otherwise trending). Pass {@code courseId = null} to aggregate across
     * the user's courses. Drives the dashboard feed's "Discovery" section.
     */
    List<GroupRecommendation> getRecommendations(UUID userId, UUID courseId);

    /**
     * The user's own "profile strength" (user_confidence + the matched threshold).
     * A pure read — never creates a profile row. Absent profile reads as zero
     * confidence. Drives the onboarding widget and the Settings matching section.
     */
    MatchingReliability getReliability(UUID userId);

    /**
     * The match score for one (user, group) pair — cached row if present, else scored
     * live. Lets the matching-feedback module stamp an explicit rating with the user's
     * match %, including for members (who aren't in their own candidate cache).
     */
    MatchSnapshot matchSnapshotFor(UUID userId, UUID groupId);
}
