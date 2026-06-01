package com.ronkadosh.bubbleup.groups.api.dto;

import com.ronkadosh.bubbleup.groups.model.GroupVisibility;
import com.ronkadosh.bubbleup.groups.model.StudyGroup;

import java.time.Instant;
import java.util.UUID;

public record GroupResponse(
        UUID id,
        String name,
        String description,
        GroupVisibility visibility,
        UUID offeringId,
        UUID createdBy,
        UUID ownerId,
        long memberCount,
        int maxMembers,
        Instant createdAt
) {
    public static GroupResponse from(StudyGroup group, UUID ownerId, long memberCount) {
        return new GroupResponse(
                group.getId(),
                group.getName(),
                group.getDescription(),
                group.getVisibility(),
                group.getOfferingId(),
                group.getCreatedBy(),
                ownerId,
                memberCount,
                group.getMaxMembers(),
                group.getCreatedAt()
        );
    }
}
