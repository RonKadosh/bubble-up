package com.ronkadosh.bubbleup.catalog.internal.dto.admin;

import java.time.Instant;
import java.util.UUID;

public record OfferingAdminDto(
        UUID id,
        UUID courseId,
        UUID termId,
        Instant createdAt
) {}
