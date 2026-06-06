package com.ronkadosh.bubbleup.groups.internal;

import com.ronkadosh.bubbleup.groups.model.GroupStatus;
import com.ronkadosh.bubbleup.groups.internal.dto.admin.GroupAdminDetail;
import com.ronkadosh.bubbleup.groups.internal.dto.admin.GroupAdminDto;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

/**
 * Admin-only surface on the groups module. Separate from {@link GroupInternalService}
 * so the cross-module read interface stays focused.
 *
 * <p>{@link #deleteGroupAsAdmin(UUID)} skips the owner + not-empty checks that the
 * member-facing {@code GroupCommandService.deleteGroup} enforces; it reuses the
 * same cascade through files, folders, calendar, chat, and members.
 */
public interface GroupAdminInternalService {

    Page<GroupAdminDto> searchForAdmin(String q, Pageable pageable);

    GroupAdminDetail getAdminDetail(UUID groupId);

    /** Cascades through files, folders, calendar events, chat rooms, members, then the group itself. */
    void deleteGroupAsAdmin(UUID groupId);

    GroupAdminDto setGroupStatus(UUID groupId, GroupStatus status);

    int setGroupStatusForOfferings(Collection<UUID> offeringIds, GroupStatus status);

    // Cross-module cascade checks used by the catalog admin path.
    long countGroupsForOffering(UUID offeringId);
    long countGroupsForOfferings(Collection<UUID> offeringIds);

    // Overview helpers.
    long countTotalGroups();
    long countGroupsByStatus(GroupStatus status);
    long countGroupsCreatedAfter(Instant since);
    List<GroupAdminDto> findRecentGroups(int limit);
}
