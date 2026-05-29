package com.ronkadosh.bubbleup.groups.api.dto;

import com.ronkadosh.bubbleup.groups.model.GroupFile;

import java.time.Instant;
import java.util.UUID;

public record GroupFileResponse(
        UUID id,
        UUID groupId,
        UUID uploaderId,
        UUID folderId,
        String originalName,
        String contentType,
        long sizeBytes,
        Instant uploadedAt
) {
    public static GroupFileResponse from(GroupFile f) {
        return new GroupFileResponse(
                f.getId(),
                f.getGroupId(),
                f.getUploaderId(),
                f.getFolderId(),
                f.getOriginalName(),
                f.getContentType(),
                f.getSizeBytes(),
                f.getUploadedAt()
        );
    }
}
