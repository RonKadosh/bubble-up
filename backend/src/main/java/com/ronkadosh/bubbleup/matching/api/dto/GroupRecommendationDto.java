package com.ronkadosh.bubbleup.matching.api.dto;

import java.util.UUID;

public record GroupRecommendationDto(
        UUID groupId,
        String groupName,
        Integer matchPercent,
        int memberCount,
        boolean alreadyMember
) {}
