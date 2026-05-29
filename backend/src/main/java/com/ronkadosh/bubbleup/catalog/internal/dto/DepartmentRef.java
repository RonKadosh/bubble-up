package com.ronkadosh.bubbleup.catalog.internal.dto;

import java.util.UUID;

public record DepartmentRef(
        UUID id,
        UUID universityId,
        String name
) {}
