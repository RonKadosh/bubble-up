package com.ronkadosh.bubbleup.matching.internal.dto;

import java.util.UUID;

/**
 * Cross-module slice of a recommended group. Drives the dashboard feed's
 * "Discovery" section. {@code matchPercent} is non-null only for MATCHED
 * recommendations; {@code type} is the batch result type ("MATCHED" / "TRENDING").
 */
public record GroupRecommendation(
        UUID groupId,
        String groupName,
        Integer matchPercent,
        int memberCount,
        boolean alreadyMember,
        String type
) {}
