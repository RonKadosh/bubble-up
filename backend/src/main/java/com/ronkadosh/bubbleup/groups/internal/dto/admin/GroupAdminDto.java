package com.ronkadosh.bubbleup.groups.internal.dto.admin;

import com.ronkadosh.bubbleup.groups.model.GroupStatus;
import com.ronkadosh.bubbleup.groups.model.GroupVisibility;

import java.time.Instant;
import java.util.UUID;

public record GroupAdminDto(
        UUID id,
        String name,
        String description,
        GroupVisibility visibility,
        GroupStatus status,
        UUID offeringId,
        UUID courseId,
        UUID createdBy,
        Instant createdAt,
        int memberCount
) {}
