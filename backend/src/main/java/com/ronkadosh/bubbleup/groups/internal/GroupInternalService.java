package com.ronkadosh.bubbleup.groups.internal;

import com.ronkadosh.bubbleup.groups.internal.dto.GroupFileActivityItem;
import com.ronkadosh.bubbleup.groups.internal.dto.GroupSummary;
import com.ronkadosh.bubbleup.groups.model.MembershipRole;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public interface GroupInternalService {

    boolean groupExists(UUID groupId);

    boolean isMember(UUID groupId, UUID userId);

    boolean isOwner(UUID groupId, UUID userId);

    Optional<MembershipRole> roleOf(UUID groupId, UUID userId);

    List<GroupSummary> getGroupsForUser(UUID userId);

    List<UUID> getMemberUserIds(UUID groupId);

    /** Files uploaded to the group since {@code since} — matching "activity" trending signal. */
    long countFilesForGroupSince(UUID groupId, java.time.Instant since);

    /** Members who joined the group since {@code since} — matching "growing" trending signal. */
    int countRecentJoinsForGroup(UUID groupId, java.time.Instant since);

    /**
     * Most-recently-uploaded files across the given groups, newest first, capped at
     * {@code limit}. Drives the "Bubble activity" feed section.
     */
    List<GroupFileActivityItem> findRecentFilesForGroups(Set<UUID> groupIds, int limit);

    /**
     * Candidate groups for matching, scoped to the given current-term offerings.
     * Each offering is queried independently (PUBLIC, excluding groups the user is
     * already in) up to {@code limitPerOffering}, so candidate selection stays
     * term-precise and never re-broadens to a course's past-term offerings.
     */
    List<UUID> getCandidateGroupIdsByOffering(List<UUID> offeringIds, UUID excludeUserId, int limitPerOffering);

    Optional<UUID> getCourseIdForGroup(UUID groupId);

    Optional<UUID> getOfferingIdForGroup(UUID groupId);

    Optional<String> getGroupName(UUID groupId);

    /**
     * True if {@code viewer} and {@code target} are both members of at least one
     * common group. Used by the profile-visibility gate ({@code GET /api/users/{id}/profile}).
     * Self-check ({@code viewer.equals(target)}) returns true without hitting the DB.
     */
    boolean usersShareAnyGroup(UUID viewer, UUID target);
}
