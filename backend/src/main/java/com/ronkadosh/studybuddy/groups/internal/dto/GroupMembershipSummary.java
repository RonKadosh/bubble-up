package com.ronkadosh.studybuddy.groups.internal.dto;

import com.ronkadosh.studybuddy.groups.model.MembershipRole;

import java.util.UUID;

public record GroupMembershipSummary(
        UUID groupId,
        UUID userId,
        MembershipRole role
) {}
