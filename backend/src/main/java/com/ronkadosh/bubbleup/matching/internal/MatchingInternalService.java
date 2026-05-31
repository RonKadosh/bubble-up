package com.ronkadosh.bubbleup.matching.internal;

import com.ronkadosh.bubbleup.matching.internal.dto.GroupRecommendation;

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
}
