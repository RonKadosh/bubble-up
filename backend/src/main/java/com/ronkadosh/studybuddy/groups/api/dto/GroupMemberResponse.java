package com.ronkadosh.studybuddy.groups.api.dto;

import com.ronkadosh.studybuddy.groups.model.GroupMember;
import com.ronkadosh.studybuddy.groups.model.MembershipRole;

import java.time.Instant;
import java.util.UUID;

public record GroupMemberResponse(
        UUID userId,
        MembershipRole role,
        Instant joinedAt
) {
    public static GroupMemberResponse from(GroupMember m) {
        return new GroupMemberResponse(m.getUserId(), m.getRole(), m.getJoinedAt());
    }
}
