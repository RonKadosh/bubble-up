package com.ronkadosh.bubbleup.enrollment.api.dto;

import java.time.Instant;
import java.util.UUID;

public record EnrollmentResponse(
        UUID id,
        UUID userId,
        UUID offeringId,
        UUID courseId,
        String courseCode,
        String courseName,
        UUID termId,
        String termCode,
        Instant createdAt
) {}
