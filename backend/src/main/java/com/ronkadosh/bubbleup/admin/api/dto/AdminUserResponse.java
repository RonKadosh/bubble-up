package com.ronkadosh.bubbleup.admin.api.dto;

import com.ronkadosh.bubbleup.auth.internal.dto.UserAdminSummary;
import com.ronkadosh.bubbleup.common.context.UserRole;

import java.time.Instant;
import java.util.UUID;

public record AdminUserResponse(
        UUID id,
        String email,
        UserRole role,
        String displayName,
        String bio,
        String avatarFileId,
        UUID universityId,
        UUID departmentId,
        Integer enrollmentYear,
        Instant createdAt
) {
    public static AdminUserResponse from(UserAdminSummary s) {
        return new AdminUserResponse(
                s.id(),
                s.email(),
                s.role(),
                s.displayName(),
                s.bio(),
                s.avatarFileId(),
                s.universityId(),
                s.departmentId(),
                s.enrollmentYear(),
                s.createdAt()
        );
    }
}
