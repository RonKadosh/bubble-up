package com.ronkadosh.studybuddy.groups.internal.dto;

import java.util.UUID;

public record GroupSummary(
        UUID id,
        String name
) {}
