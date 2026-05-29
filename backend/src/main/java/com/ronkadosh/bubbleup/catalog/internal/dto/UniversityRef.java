package com.ronkadosh.bubbleup.catalog.internal.dto;

import java.util.UUID;

public record UniversityRef(
        UUID id,
        String name,
        String shortCode
) {}
