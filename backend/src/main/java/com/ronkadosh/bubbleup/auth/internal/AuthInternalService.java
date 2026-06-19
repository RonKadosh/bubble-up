package com.ronkadosh.bubbleup.auth.internal;

import com.ronkadosh.bubbleup.auth.internal.dto.AuthSession;
import com.ronkadosh.bubbleup.auth.internal.dto.UserAdminFilter;
import com.ronkadosh.bubbleup.auth.internal.dto.UserAdminSummary;
import com.ronkadosh.bubbleup.auth.internal.dto.UserIdentity;
import com.ronkadosh.bubbleup.auth.internal.dto.UserProfile;
import com.ronkadosh.bubbleup.auth.model.UserStatus;
import com.ronkadosh.bubbleup.common.context.UserRole;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public interface AuthInternalService {
    boolean userExists(UUID userId);
    Optional<String> getEmail(UUID userId);

    /**
     * Affiliation snapshot for {@code userId}. Read on demand so a profile update
     * is reflected immediately without re-issuing the JWT.
     */
    Optional<UserProfile> getProfile(UUID userId);

    /**
     * Cheap identity slice (display name + avatar) for a single user. Used by
     * the chat send path where we already have a single sender id.
     */
    Optional<UserIdentity> getIdentity(UUID userId);

    /**
     * Batch identity lookup used by paginated list responses (chat history,
     * member rosters). Single round-trip per page; absent ids are simply
     * missing from the returned map.
     */
    Map<UUID, UserIdentity> getIdentitiesByIds(Collection<UUID> userIds);

    /**
     * Sets the user's role to {@code EXPERT}. Idempotent: no-op if already EXPERT
     * or ADMIN. Throws {@code USER_NOT_FOUND} if the user does not exist.
     */
    void promoteToExpert(UUID userId);

    /**
     * Issues a fresh access + refresh token pair for an existing user WITHOUT
     * credentials. Sole caller is the demo module's guest auto-login; the same
     * {@code JwtService} + {@code RefreshTokenService} path used by normal login.
     * Throws {@code USER_NOT_FOUND} if the user does not exist.
     */
    AuthSession issueSession(UUID userId);

    // ─── Admin panel surface ───────────────────────────────────────────────────

    /** Full admin-view of a user. Empty if the user does not exist. */
    Optional<UserAdminSummary> getAdminSummary(UUID userId);

    /** Paginated admin search with optional role + free-text + registration-window filters. */
    Page<UserAdminSummary> searchForAdmin(UserAdminFilter filter, Pageable pageable);

    /** Sets a user's role. Throws {@code USER_NOT_FOUND} if the user does not exist. */
    void changeRole(UUID userId, UserRole newRole);

    void setUserStatus(UUID userId, UserStatus status, Instant suspendedUntil, String reason);

    /**
     * Sets the user's role to {@code STUDENT}. Idempotent: no-op if already STUDENT.
     * Throws {@code USER_NOT_FOUND} if the user does not exist. Mirrors
     * {@link #promoteToExpert(UUID)}; used by expert-rejection in the admin panel.
     */
    void demoteToStudent(UUID userId);

    long countByRole(UserRole role);

    /** Single-query role distribution for the admin Overview. */
    Map<UserRole, Long> countByRoleAll();

    long countCreatedAfter(Instant since);

    /** Newest {@code limit} users, in createdAt-DESC order. Used by the admin activity feed. */
    List<UserAdminSummary> findRecentForAdmin(int limit);
}
