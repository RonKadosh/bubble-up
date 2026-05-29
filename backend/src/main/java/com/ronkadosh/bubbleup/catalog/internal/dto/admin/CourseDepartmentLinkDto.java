package com.ronkadosh.bubbleup.catalog.internal.dto.admin;

import java.time.Instant;
import java.util.UUID;

public record CourseDepartmentLinkDto(
        UUID courseId,
        UUID departmentId,
        boolean primary,
        Instant createdAt
) {}
