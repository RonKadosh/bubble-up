package com.ronkadosh.bubbleup.groups.internal.dto.admin;

import com.ronkadosh.bubbleup.groups.model.MembershipRole;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record GroupAdminDetail(
        GroupAdminDto group,
        List<Member> members
) {
    public record Member(
            UUID userId,
            String email,
            String displayName,
            MembershipRole role,
            Instant joinedAt
    ) {}
}
