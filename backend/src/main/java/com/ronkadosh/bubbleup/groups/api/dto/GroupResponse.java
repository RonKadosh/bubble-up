package com.ronkadosh.bubbleup.groups.api.dto;

import com.ronkadosh.bubbleup.common.api.ApiPaths;
import com.ronkadosh.bubbleup.groups.model.GroupVisibility;
import com.ronkadosh.bubbleup.groups.model.GroupStatus;
import com.ronkadosh.bubbleup.groups.model.StudyGroup;

import java.time.Instant;
import java.util.UUID;

public record GroupResponse(
        UUID id,
        String name,
        String description,
        GroupVisibility visibility,
        GroupStatus status,
        UUID offeringId,
        UUID createdBy,
        UUID ownerId,
        long memberCount,
        int maxMembers,
        /**
         * Cache-busted public cover-image URL ({@code /api/groups/{id}/image?v={fileId}})
         * when a cover image is set, else null — the frontend falls back to the
         * generated gradient avatar. Cache-busted on {@code imageFileId} so a new
         * upload always invalidates the old immutable-cached image.
         */
        String imageUrl,
        Instant createdAt
) {
    public static GroupResponse from(StudyGroup group, UUID ownerId, long memberCount) {
        String imageUrl = group.getImageFileId() == null
                ? null
                : ApiPaths.GROUPS_BASE + "/" + group.getId() + "/image?v=" + group.getImageFileId();
        return new GroupResponse(
                group.getId(),
                group.getName(),
                group.getDescription(),
                group.getVisibility(),
                group.getStatus(),
                group.getOfferingId(),
                group.getCreatedBy(),
                ownerId,
                memberCount,
                group.getMaxMembers(),
                imageUrl,
                group.getCreatedAt()
        );
    }
}
