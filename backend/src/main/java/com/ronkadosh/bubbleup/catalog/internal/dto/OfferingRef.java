package com.ronkadosh.bubbleup.catalog.internal.dto;

import java.util.UUID;

public record OfferingRef(
        UUID id,
        UUID courseId,
        UUID termId,
        UUID universityId,
        String courseCode,
        String courseName,
        String termCode
) {}
