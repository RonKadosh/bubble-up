package com.ronkadosh.bubbleup.catalog.internal.dto.admin;

import java.time.Instant;
import java.util.UUID;

public record DepartmentAdminDto(
        UUID id,
        UUID universityId,
        String name,
        String shortCode,
        Instant createdAt
) {}
