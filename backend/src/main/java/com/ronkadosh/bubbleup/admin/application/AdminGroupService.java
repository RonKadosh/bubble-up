package com.ronkadosh.bubbleup.admin.application;

import com.ronkadosh.bubbleup.admin.api.dto.AdminGroupDtos;
import com.ronkadosh.bubbleup.admin.audit.AdminAuditService;
import com.ronkadosh.bubbleup.catalog.internal.CatalogInternalService;
import com.ronkadosh.bubbleup.common.pagination.PageMapper;
import com.ronkadosh.bubbleup.common.pagination.PageResponseDto;
import com.ronkadosh.bubbleup.groups.internal.GroupAdminInternalService;
import com.ronkadosh.bubbleup.groups.model.GroupStatus;
import com.ronkadosh.bubbleup.matching.internal.MatchingAdminInternalService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AdminGroupService {

    private final GroupAdminInternalService groupAdmin;
    private final MatchingAdminInternalService matchingAdmin;
    private final CatalogInternalService catalogInternalService;
    private final AdminAuditService audit;
    private final AdminGuards guards;
    private final PageMapper pageMapper;

    public PageResponseDto<AdminGroupDtos.GroupResponse> search(
            String q, int page, int size, String sortBy, String sortDirection
    ) {
        Sort.Direction dir = Sort.Direction.fromString(sortDirection);
        Pageable pageable = PageRequest.of(page, size, Sort.by(dir, sortBy));
        var result = groupAdmin.searchForAdmin(q, pageable).map(AdminGroupDtos.GroupResponse::from);
        return pageMapper.toDto(result);
    }

    public AdminGroupDtos.GroupDetailResponse getDetail(UUID groupId) {
        return AdminGroupDtos.GroupDetailResponse.from(groupAdmin.getAdminDetail(groupId));
    }

    public void delete(UUID groupId, AdminGroupDtos.DeleteGroupRequest req) {
        guards.requireReason(req.reason());
        groupAdmin.deleteGroupAsAdmin(groupId);
        audit.record("GROUP_DELETED", "GROUP", groupId, req.reason(), null);
    }

    public AdminGroupDtos.GroupResponse archive(UUID groupId, AdminGroupDtos.ModerateGroupRequest req) {
        guards.requireReason(req.reason());
        var group = AdminGroupDtos.GroupResponse.from(groupAdmin.setGroupStatus(groupId, GroupStatus.ARCHIVED));
        matchingAdmin.purgeGroupRecommendations(groupId);
        audit.record("GROUP_ARCHIVED", "GROUP", groupId, req.reason(), null);
        return group;
    }

    public AdminGroupDtos.GroupResponse activate(UUID groupId, AdminGroupDtos.ModerateGroupRequest req) {
        guards.requireReason(req.reason());
        var group = AdminGroupDtos.GroupResponse.from(groupAdmin.setGroupStatus(groupId, GroupStatus.ACTIVE));
        matchingAdmin.purgeGroupRecommendations(groupId);
        audit.record("GROUP_ACTIVATED", "GROUP", groupId, req.reason(), null);
        return group;
    }

    public AdminGroupDtos.BulkModerationResponse archiveTermGroups(UUID termId, AdminGroupDtos.ModerateGroupRequest req) {
        guards.requireReason(req.reason());
        var offeringIds = catalogInternalService.offeringIdsForTerm(termId);
        int affected = groupAdmin.setGroupStatusForOfferings(offeringIds, GroupStatus.ARCHIVED);
        audit.record("TERM_GROUPS_ARCHIVED", "TERM", termId, req.reason(),
                "{\"affectedGroups\":" + affected + "}");
        return new AdminGroupDtos.BulkModerationResponse(affected);
    }
}
