package com.ronkadosh.studybuddy.groups.api.dto;

import com.ronkadosh.studybuddy.groups.model.GroupFile;

import java.time.Instant;
import java.util.UUID;

public record GroupFileResponse(
        UUID id,
        UUID groupId,
        UUID uploaderId,
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
                f.getOriginalName(),
                f.getContentType(),
                f.getSizeBytes(),
                f.getUploadedAt()
        );
    }
}
