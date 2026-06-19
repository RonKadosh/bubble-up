package com.ronkadosh.bubbleup.auth.application;

import com.ronkadosh.bubbleup.auth.internal.AuthInternalService;
import com.ronkadosh.bubbleup.auth.internal.dto.AuthSession;
import com.ronkadosh.bubbleup.auth.internal.dto.UserAdminFilter;
import com.ronkadosh.bubbleup.auth.internal.dto.UserAdminSummary;
import com.ronkadosh.bubbleup.auth.internal.dto.UserIdentity;
import com.ronkadosh.bubbleup.auth.internal.dto.UserProfile;
import com.ronkadosh.bubbleup.auth.model.User;
import com.ronkadosh.bubbleup.auth.model.UserStatus;
import com.ronkadosh.bubbleup.auth.persistence.UserRepository;
import com.ronkadosh.bubbleup.common.context.UserRole;
import com.ronkadosh.bubbleup.common.error.AppException;
import com.ronkadosh.bubbleup.common.error.ErrorCode;
import com.ronkadosh.bubbleup.common.security.JwtService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Collection;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AuthInternalServiceImpl implements AuthInternalService {

    private final UserRepository userRepository;
    private final JwtService jwtService;
    private final RefreshTokenService refreshTokenService;

    @Override
    @Transactional(readOnly = true)
    public boolean userExists(UUID userId) {
        return userRepository.existsById(userId);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<String> getEmail(UUID userId) {
        return userRepository.findById(userId).map(User::getEmail);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<UserProfile> getProfile(UUID userId) {
        return userRepository.findById(userId)
                .map(u -> new UserProfile(
                        u.getId(),
                        u.getDisplayName(),
                        u.getBio(),
                        u.getAvatarFileId(),
                        u.getUniversityId(),
                        u.getDepartmentId(),
                        u.getEnrollmentYear()));
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<UserIdentity> getIdentity(UUID userId) {
        return userRepository.findById(userId).map(AuthInternalServiceImpl::toIdentity);
    }

    @Override
    @Transactional(readOnly = true)
    public Map<UUID, UserIdentity> getIdentitiesByIds(Collection<UUID> userIds) {
        if (userIds == null || userIds.isEmpty()) return Map.of();
        return userRepository.findAllById(userIds).stream()
                .collect(Collectors.toMap(User::getId, AuthInternalServiceImpl::toIdentity));
    }

    @Override
    @Transactional
    public void promoteToExpert(UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));
        if (user.getRole() == UserRole.ADMIN || user.getRole() == UserRole.EXPERT) {
            return;
        }
        user.setRole(UserRole.EXPERT);
        userRepository.save(user);
    }

    @Override
    @Transactional
    public AuthSession issueSession(UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));
        String accessToken = jwtService.generateToken(user.getId(), user.getEmail(), user.getRole());
        RefreshTokenService.IssuedRefresh refresh = refreshTokenService.issue(user.getId(), null);
        UserIdentity identity = toIdentity(user);
        boolean emailVerified = user.getGoogleSub() == null || user.isEmailVerified();
        return new AuthSession(
                accessToken,
                refresh.rawToken(),
                user.getId(),
                user.getEmail(),
                user.getRole().name(),
                user.getDisplayName(),
                identity.avatarUrl(),
                emailVerified);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<UserAdminSummary> getAdminSummary(UUID userId) {
        return userRepository.findById(userId).map(AuthInternalServiceImpl::toAdminSummary);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<UserAdminSummary> searchForAdmin(UserAdminFilter filter, Pageable pageable) {
        // Sentinel-driven "no filter" values — Postgres can't infer types of
        // parameters used only in `IS NULL` predicates (it falls back to bytea
        // for Strings and refuses to parse Instants). Sending an empty string
        // and Instant.EPOCH lets every row through while keeping the parameter
        // types unambiguous. See backend/CLAUDE.md gaps for context.
        String q = filter.q() == null || filter.q().isBlank()
                ? ""
                : filter.q().trim().toLowerCase(Locale.ROOT);
        Instant createdAfter = filter.createdAfter() == null
                ? Instant.EPOCH
                : filter.createdAfter();
        return userRepository.searchForAdmin(filter.role(), filter.status(), q, createdAfter, pageable)
                .map(AuthInternalServiceImpl::toAdminSummary);
    }

    @Override
    @Transactional
    public void changeRole(UUID userId, UserRole newRole) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));
        user.setRole(newRole);
        userRepository.save(user);
    }

    @Override
    @Transactional
    public void setUserStatus(UUID userId, UserStatus status, Instant suspendedUntil, String reason) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));
        user.setStatus(status);
        user.setSuspendedUntil(status == UserStatus.SUSPENDED ? suspendedUntil : null);
        user.setStatusReason(status == UserStatus.ACTIVE ? null : reason);
        userRepository.save(user);
    }

    @Override
    @Transactional
    public void demoteToStudent(UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));
        if (user.getRole() == UserRole.STUDENT) {
            return;
        }
        user.setRole(UserRole.STUDENT);
        userRepository.save(user);
    }

    @Override
    @Transactional(readOnly = true)
    public long countByRole(UserRole role) {
        return userRepository.countByRole(role);
    }

    @Override
    @Transactional(readOnly = true)
    public Map<UserRole, Long> countByRoleAll() {
        EnumMap<UserRole, Long> result = new EnumMap<>(UserRole.class);
        for (UserRole role : UserRole.values()) {
            result.put(role, userRepository.countByRole(role));
        }
        return result;
    }

    @Override
    @Transactional(readOnly = true)
    public long countCreatedAfter(Instant since) {
        return userRepository.countByCreatedAtAfter(since);
    }

    @Override
    @Transactional(readOnly = true)
    public List<UserAdminSummary> findRecentForAdmin(int limit) {
        List<User> recent = userRepository.findTop50ByOrderByCreatedAtDesc();
        if (limit >= recent.size()) {
            return recent.stream().map(AuthInternalServiceImpl::toAdminSummary).toList();
        }
        return recent.subList(0, limit).stream()
                .map(AuthInternalServiceImpl::toAdminSummary)
                .toList();
    }

    private static UserIdentity toIdentity(User u) {
        return new UserIdentity(u.getId(), u.getDisplayName(), u.getAvatarFileId());
    }

    private static UserAdminSummary toAdminSummary(User u) {
        return new UserAdminSummary(
                u.getId(),
                u.getEmail(),
                u.getRole(),
                u.getDisplayName(),
                u.getBio(),
                u.getAvatarFileId(),
                u.getUniversityId(),
                u.getDepartmentId(),
                u.getEnrollmentYear(),
                u.getStatus(),
                u.getSuspendedUntil(),
                u.getStatusReason(),
                u.getCreatedAt()
        );
    }
}
