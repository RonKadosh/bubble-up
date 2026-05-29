package com.ronkadosh.bubbleup.admin.application;

import com.ronkadosh.bubbleup.auth.internal.AuthInternalService;
import com.ronkadosh.bubbleup.common.context.CurrentUser;
import com.ronkadosh.bubbleup.common.context.UserRole;
import com.ronkadosh.bubbleup.common.error.AppException;
import com.ronkadosh.bubbleup.common.error.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Cross-cutting checks every destructive admin action runs in the same order:
 * <ol>
 *   <li>{@link #requireReason(String)} — written reason is non-blank.</li>
 *   <li>{@link #requireNotSelf(UUID, CurrentUser)} — admin can't act on themselves.</li>
 *   <li>{@link #requireNotLastAdmin(UUID)} — never strip the last functional admin.</li>
 * </ol>
 *
 * <p>Reason validation typically happens at the request DTO level via {@code @NotBlank},
 * but {@link #requireReason(String)} is exposed for the few endpoints that take an
 * optional reason (e.g. verify) and want to enforce it conditionally.
 */
@Component
@RequiredArgsConstructor
public class AdminGuards {

    private final AuthInternalService authInternalService;

    public void requireReason(String reason) {
        if (reason == null || reason.isBlank()) {
            throw new AppException(ErrorCode.ADMIN_REASON_REQUIRED);
        }
    }

    public void requireNotSelf(UUID targetUserId, CurrentUser me) {
        if (targetUserId == null) return;
        if (targetUserId.equals(me.id())) {
            throw new AppException(ErrorCode.ADMIN_FORBIDDEN_SELF_ACTION);
        }
    }

    /**
     * Reject if removing/demoting/banning {@code targetUserId} would leave zero admins.
     * In Phase A "functional" = role == ADMIN. Phase B will refine this to
     * {@code role == ADMIN AND !isBanned AND !isDeleted} once those flags exist.
     */
    public void requireNotLastAdmin(UUID targetUserId) {
        long total = authInternalService.countByRole(UserRole.ADMIN);
        boolean targetIsAdmin = authInternalService.getAdminSummary(targetUserId)
                .map(s -> s.role() == UserRole.ADMIN)
                .orElse(false);
        if (targetIsAdmin && total <= 1) {
            throw new AppException(ErrorCode.ADMIN_LAST_ADMIN_PROTECTED);
        }
    }
}
