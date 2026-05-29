package com.ronkadosh.bubbleup.catalog.internal.dto.admin;

import java.time.Instant;
import java.util.UUID;

public record UniversityAdminDto(
        UUID id,
        String name,
        String shortCode,
        String country,
        Instant createdAt
) {}
