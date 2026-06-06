package com.ronkadosh.bubbleup.admin.application;

import com.ronkadosh.bubbleup.admin.api.dto.AdminUserResponse;
import com.ronkadosh.bubbleup.admin.api.dto.ChangeRoleRequest;
import com.ronkadosh.bubbleup.admin.api.dto.ModerateUserRequest;
import com.ronkadosh.bubbleup.admin.audit.AdminAuditService;
import com.ronkadosh.bubbleup.auth.application.RefreshTokenService;
import com.ronkadosh.bubbleup.auth.internal.AuthInternalService;
import com.ronkadosh.bubbleup.auth.internal.dto.UserAdminFilter;
import com.ronkadosh.bubbleup.auth.model.UserStatus;
import com.ronkadosh.bubbleup.common.context.CurrentUser;
import com.ronkadosh.bubbleup.common.context.UserRole;
import com.ronkadosh.bubbleup.common.error.AppException;
import com.ronkadosh.bubbleup.common.error.ErrorCode;
import com.ronkadosh.bubbleup.common.pagination.PageMapper;
import com.ronkadosh.bubbleup.common.pagination.PageResponseDto;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AdminUserService {

    private final AuthInternalService authInternalService;
    private final RefreshTokenService refreshTokenService;
    private final AdminAuditService audit;
    private final AdminGuards guards;
    private final PageMapper pageMapper;

    public PageResponseDto<AdminUserResponse> search(
            UserRole role, UserStatus status, String q, Instant createdAfter,
            int page, int size, String sortBy, String sortDirection
    ) {
        Sort.Direction dir = Sort.Direction.fromString(sortDirection);
        Pageable pageable = PageRequest.of(page, size, Sort.by(dir, sortBy));
        var filter = new UserAdminFilter(role, status, q, createdAfter);
        var resultPage = authInternalService.searchForAdmin(filter, pageable).map(AdminUserResponse::from);
        return pageMapper.toDto(resultPage);
    }

    public AdminUserResponse get(UUID userId) {
        return authInternalService.getAdminSummary(userId)
                .map(AdminUserResponse::from)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));
    }

    public AdminUserResponse changeRole(UUID targetUserId, ChangeRoleRequest req, CurrentUser me) {
        guards.requireReason(req.reason());
        guards.requireNotSelf(targetUserId, me);
        // Demoting an existing ADMIN to a lower role is what we need to protect.
        if (req.newRole() != UserRole.ADMIN) {
            guards.requireNotLastAdmin(targetUserId);
        }
        // Touch the user to validate it exists before applying.
        authInternalService.getAdminSummary(targetUserId)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));
        authInternalService.changeRole(targetUserId, req.newRole());
        audit.record("USER_ROLE_CHANGED", "USER", targetUserId, req.reason(),
                "{\"newRole\":\"" + req.newRole().name() + "\"}");
        return get(targetUserId);
    }

    public AdminUserResponse suspend(UUID targetUserId, ModerateUserRequest req, CurrentUser me) {
        guards.requireReason(req.reason());
        guards.requireNotSelf(targetUserId, me);
        authInternalService.getAdminSummary(targetUserId)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));
        authInternalService.setUserStatus(targetUserId, UserStatus.SUSPENDED, req.suspendedUntil(), req.reason());
        refreshTokenService.revokeAllForUser(targetUserId);
        audit.record("USER_SUSPENDED", "USER", targetUserId, req.reason(),
                req.suspendedUntil() == null ? null : "{\"suspendedUntil\":\"" + req.suspendedUntil() + "\"}");
        return get(targetUserId);
    }

    public AdminUserResponse ban(UUID targetUserId, ModerateUserRequest req, CurrentUser me) {
        guards.requireReason(req.reason());
        guards.requireNotSelf(targetUserId, me);
        authInternalService.getAdminSummary(targetUserId)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));
        guards.requireNotLastAdmin(targetUserId);
        authInternalService.setUserStatus(targetUserId, UserStatus.BANNED, null, req.reason());
        refreshTokenService.revokeAllForUser(targetUserId);
        audit.record("USER_BANNED", "USER", targetUserId, req.reason(), null);
        return get(targetUserId);
    }

    public AdminUserResponse reactivate(UUID targetUserId, ModerateUserRequest req, CurrentUser me) {
        guards.requireReason(req.reason());
        guards.requireNotSelf(targetUserId, me);
        authInternalService.getAdminSummary(targetUserId)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));
        authInternalService.setUserStatus(targetUserId, UserStatus.ACTIVE, null, null);
        audit.record("USER_REACTIVATED", "USER", targetUserId, req.reason(), null);
        return get(targetUserId);
    }
}
