package com.ronkadosh.bubbleup.auth.internal.dto;

import com.ronkadosh.bubbleup.common.context.UserRole;

import java.time.Instant;
import java.util.UUID;

public record UserAdminSummary(
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
) {}
