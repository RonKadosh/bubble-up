package com.ronkadosh.bubbleup.groups.api.dto;

import com.ronkadosh.bubbleup.auth.internal.dto.UserIdentity;
import com.ronkadosh.bubbleup.groups.model.GroupMember;
import com.ronkadosh.bubbleup.groups.model.MembershipRole;

import java.time.Instant;
import java.util.UUID;

public record GroupMemberResponse(
        UUID userId,
        String displayName,
        String avatarUrl,
        MembershipRole role,
        Instant joinedAt
) {
    /**
     * {@code identity} may be null (deleted user / orphan row) — the frontend
     * falls back to the gradient-initial Avatar in that case.
     */
    public static GroupMemberResponse from(GroupMember m, UserIdentity identity) {
        String displayName = identity == null ? null : identity.displayName();
        String avatarUrl = identity == null ? null : identity.avatarUrl();
        return new GroupMemberResponse(m.getUserId(), displayName, avatarUrl, m.getRole(), m.getJoinedAt());
    }
}
