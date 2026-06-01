package com.ronkadosh.bubbleup.matching.internal.dto;

import java.util.List;
import java.util.UUID;

/**
 * Cross-module slice of a recommended group. Drives the dashboard feed's
 * "Discovery" section. {@code matchPercent} is non-null only when {@code displayMode}
 * is MATCHED; for TRENDING, {@code reasonLabels} carries the machine codes
 * (TRENDING_ACTIVE / _GROWING / _POPULAR / _UPCOMING) the client localizes.
 */
public record GroupRecommendation(
        UUID groupId,
        String groupName,
        Integer matchPercent,
        int memberCount,
        boolean alreadyMember,
        String displayMode,
        List<String> reasonLabels
) {}
