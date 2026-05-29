package com.ronkadosh.bubbleup.catalog.internal.dto.admin;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record CourseAdminDto(
        UUID id,
        UUID universityId,
        String code,
        String name,
        BigDecimal creditPoints,
        String description,
        Instant createdAt
) {}
