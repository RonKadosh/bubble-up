package com.ronkadosh.bubbleup.admin.api;

import com.ronkadosh.bubbleup.admin.api.dto.AdminUserResponse;
import com.ronkadosh.bubbleup.admin.api.dto.ChangeRoleRequest;
import com.ronkadosh.bubbleup.admin.application.AdminUserService;
import com.ronkadosh.bubbleup.common.api.ApiPaths;
import com.ronkadosh.bubbleup.common.api.ApiResponse;
import com.ronkadosh.bubbleup.common.context.CurrentUserProvider;
import com.ronkadosh.bubbleup.common.context.UserRole;
import com.ronkadosh.bubbleup.common.pagination.PageResponseDto;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.UUID;

@RestController
@RequestMapping(ApiPaths.ADMIN_BASE + "/users")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class AdminUserController {

    private final AdminUserService service;
    private final CurrentUserProvider currentUserProvider;

    @GetMapping
    public ApiResponse<PageResponseDto<AdminUserResponse>> search(
            @RequestParam(required = false) UserRole role,
            @RequestParam(required = false) String q,
            @RequestParam(required = false) Instant createdAfter,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @RequestParam(defaultValue = "desc") String sortDirection
    ) {
        return ApiResponse.success(service.search(role, q, createdAfter, page, size, sortBy, sortDirection));
    }

    @GetMapping("/{id}")
    public ApiResponse<AdminUserResponse> get(@PathVariable UUID id) {
        return ApiResponse.success(service.get(id));
    }

    @PatchMapping("/{id}/role")
    public ApiResponse<AdminUserResponse> changeRole(
            @PathVariable UUID id,
            @Valid @RequestBody ChangeRoleRequest req
    ) {
        return ApiResponse.success(service.changeRole(id, req, currentUserProvider.get()));
    }
}
