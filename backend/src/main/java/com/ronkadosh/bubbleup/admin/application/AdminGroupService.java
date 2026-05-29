package com.ronkadosh.bubbleup.admin.application;

import com.ronkadosh.bubbleup.admin.api.dto.AdminGroupDtos;
import com.ronkadosh.bubbleup.common.pagination.PageMapper;
import com.ronkadosh.bubbleup.common.pagination.PageResponseDto;
import com.ronkadosh.bubbleup.groups.internal.GroupAdminInternalService;
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
    }
}
