package com.ronkadosh.studybuddy.groups.api.dto;

import com.ronkadosh.studybuddy.groups.model.GroupVisibility;
import com.ronkadosh.studybuddy.groups.model.StudyGroup;

import java.time.Instant;
import java.util.UUID;

public record GroupResponse(
        UUID id,
        String name,
        String description,
        GroupVisibility visibility,
        UUID createdBy,
        UUID ownerId,
        long memberCount,
        Instant createdAt
) {
    public static GroupResponse from(StudyGroup group, UUID ownerId, long memberCount) {
        return new GroupResponse(
                group.getId(),
                group.getName(),
                group.getDescription(),
                group.getVisibility(),
                group.getCreatedBy(),
                ownerId,
                memberCount,
                group.getCreatedAt()
        );
    }
}
