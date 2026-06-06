package com.ronkadosh.bubbleup.matching.api.dto;

import java.util.List;
import java.util.UUID;

/**
 * One recommended bubble. {@code displayMode} = MATCHED → trust the personalized fit,
 * show {@code matchPercent}; TRENDING → show {@code reasonLabels} (machine codes the
 * client localizes), {@code matchPercent} is null. {@code matchingConfidence} is the
 * raw user×group trust that decided the mode (exposed for debugging / future UI).
 */
public record GroupRecommendationDto(
        UUID groupId,
        String groupName,
        String courseCode,
        String courseName,
        Integer matchPercent,
        int memberCount,
        boolean alreadyMember,
        String displayMode,
        double matchingConfidence,
        List<String> reasonLabels
) {}
