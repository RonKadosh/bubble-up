package com.ronkadosh.bubbleup.groups.api.dto;

import com.ronkadosh.bubbleup.groups.model.GroupFolder;

import java.time.Instant;
import java.util.UUID;

public record GroupFolderResponse(
        UUID id,
        UUID groupId,
        UUID parentId,
        String name,
        UUID createdById,
        Instant createdAt
) {
    public static GroupFolderResponse from(GroupFolder f) {
        return new GroupFolderResponse(
                f.getId(),
                f.getGroupId(),
                f.getParentId(),
                f.getName(),
                f.getCreatedById(),
                f.getCreatedAt()
        );
    }
}
