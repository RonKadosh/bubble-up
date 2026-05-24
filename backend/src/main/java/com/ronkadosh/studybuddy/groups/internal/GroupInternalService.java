package com.ronkadosh.studybuddy.groups.internal;

import com.ronkadosh.studybuddy.groups.internal.dto.GroupSummary;
import com.ronkadosh.studybuddy.groups.model.MembershipRole;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface GroupInternalService {

    boolean groupExists(UUID groupId);

    boolean isMember(UUID groupId, UUID userId);

    boolean isOwner(UUID groupId, UUID userId);

    Optional<MembershipRole> roleOf(UUID groupId, UUID userId);

    List<GroupSummary> getGroupsForUser(UUID userId);
}
