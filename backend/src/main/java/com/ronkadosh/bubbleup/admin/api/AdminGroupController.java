package com.ronkadosh.bubbleup.admin.api;

import com.ronkadosh.bubbleup.admin.api.dto.AdminGroupDtos;
import com.ronkadosh.bubbleup.admin.application.AdminGroupService;
import com.ronkadosh.bubbleup.common.api.ApiPaths;
import com.ronkadosh.bubbleup.common.api.ApiResponse;
import com.ronkadosh.bubbleup.common.pagination.PageResponseDto;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping(ApiPaths.ADMIN_BASE + "/groups")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class AdminGroupController {

    private final AdminGroupService service;

    @GetMapping
    public ApiResponse<PageResponseDto<AdminGroupDtos.GroupResponse>> search(
            @RequestParam(required = false) String q,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @RequestParam(defaultValue = "desc") String sortDirection
    ) {
        return ApiResponse.success(service.search(q, page, size, sortBy, sortDirection));
    }

    @GetMapping("/{id}")
    public ApiResponse<AdminGroupDtos.GroupDetailResponse> getDetail(@PathVariable UUID id) {
        return ApiResponse.success(service.getDetail(id));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(
            @PathVariable UUID id,
            @Valid @RequestBody AdminGroupDtos.DeleteGroupRequest req
    ) {
        service.delete(id, req);
        return ApiResponse.success(null);
    }

    @PostMapping("/{id}/archive")
    public ApiResponse<AdminGroupDtos.GroupResponse> archive(
            @PathVariable UUID id,
            @Valid @RequestBody AdminGroupDtos.ModerateGroupRequest req
    ) {
        return ApiResponse.success(service.archive(id, req));
    }

    @PostMapping("/{id}/activate")
    public ApiResponse<AdminGroupDtos.GroupResponse> activate(
            @PathVariable UUID id,
            @Valid @RequestBody AdminGroupDtos.ModerateGroupRequest req
    ) {
        return ApiResponse.success(service.activate(id, req));
    }

    @PostMapping("/terms/{termId}/archive")
    public ApiResponse<AdminGroupDtos.BulkModerationResponse> archiveTermGroups(
            @PathVariable UUID termId,
            @Valid @RequestBody AdminGroupDtos.ModerateGroupRequest req
    ) {
        return ApiResponse.success(service.archiveTermGroups(termId, req));
    }
}
