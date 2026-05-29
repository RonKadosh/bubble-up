package com.ronkadosh.bubbleup.groups.internal.dto;

import com.ronkadosh.bubbleup.groups.model.MembershipRole;

import java.util.UUID;

public record GroupMembershipSummary(
        UUID groupId,
        UUID userId,
        MembershipRole role
) {}
