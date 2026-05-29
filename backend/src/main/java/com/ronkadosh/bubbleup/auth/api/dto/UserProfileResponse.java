package com.ronkadosh.bubbleup.auth.api.dto;

import com.ronkadosh.bubbleup.auth.internal.dto.UserIdentity;
import com.ronkadosh.bubbleup.auth.internal.dto.UserProfile;
import com.ronkadosh.bubbleup.common.context.UserRole;

import java.time.Instant;
import java.util.UUID;

/**
 * Wire shape for {@code GET /api/users/me/profile} and
 * {@code GET /api/users/{userId}/profile}.
 *
 * <p>{@code avatarUrl} is the cache-busted form
 * ({@code /api/users/{id}/avatar?v={fileId}}) when an avatar is set, else null —
 * built via {@link UserIdentity#avatarUrl()} so chat/members/profile share one
 * construction site.
 *
 * <p>{@code universityName} / {@code departmentName} are denormalized server-side
 * (via {@code CatalogInternalService}) so the profile page doesn't need to fetch
 * the catalog separately to render the line "Ben-Gurion · Software Engineering · Year 3".
 */
public record UserProfileResponse(
        UUID userId,
        String displayName,
        String bio,
        String avatarUrl,
        UUID universityId,
        String universityName,
        UUID departmentId,
        String departmentName,
        Integer enrollmentYear,
        UserRole role,
        Instant createdAt
) {
    public static UserProfileResponse from(
            UserProfile p,
            String universityName,
            String departmentName,
            UserRole role,
            Instant createdAt
    ) {
        // UserIdentity owns the avatar-URL builder so other modules don't need
        // to import this API DTO just to construct one. We just borrow it here.
        String avatarUrl = new UserIdentity(p.userId(), p.displayName(), p.avatarFileId()).avatarUrl();
        return new UserProfileResponse(
                p.userId(),
                p.displayName(),
                p.bio(),
                avatarUrl,
                p.universityId(),
                universityName,
                p.departmentId(),
                departmentName,
                p.enrollmentYear(),
                role,
                createdAt);
    }
}
