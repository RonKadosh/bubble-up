package com.ronkadosh.bubbleup.matching.api.dto;

import java.util.List;

public record RecommendationsResponse(
        String type,
        double reliability,
        List<GroupRecommendationDto> groups
) {}
