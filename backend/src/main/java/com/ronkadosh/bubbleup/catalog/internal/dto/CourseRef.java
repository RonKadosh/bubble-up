package com.ronkadosh.bubbleup.catalog.internal.dto;

import java.util.UUID;

public record CourseRef(
        UUID id,
        UUID universityId,
        String code,
        String name
) {}
