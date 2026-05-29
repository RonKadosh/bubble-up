package com.ronkadosh.bubbleup.auth.internal.dto;

import java.util.UUID;

/**
 * Cross-module read shape for a user's academic affiliation. Returned by
 * {@code AuthInternalService.getProfile(userId)} so other modules (e.g. {@code groups/})
 * can scope queries to the caller's university/department without re-reading the
 * {@code users} table directly.
 *
 * <p>All affiliation fields are nullable — a user can exist without affiliation
 * set (intentional, to keep registration friction-free for QA). Relevance
 * endpoints reject unset affiliation with {@code USER_AFFILIATION_REQUIRED}.
 *
 * <p>Identity fields ({@code displayName}, {@code avatarFileId}) are also on the
 * {@link UserIdentity} record — duplicated here so a single
 * {@code getProfile(userId)} call gives a profile page everything it needs
 * without a second lookup. {@code UserIdentity} stays the right shape for
 * batch list rendering where affiliation isn't needed.
 */
public record UserProfile(
        UUID userId,
        String displayName,
        String bio,
        String avatarFileId,
        UUID universityId,
        UUID departmentId,
        Integer enrollmentYear
) {
    public boolean hasAffiliation() {
        return universityId != null && departmentId != null;
    }
}
