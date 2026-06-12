package com.ronkadosh.bubbleup.auth.application;

import com.ronkadosh.bubbleup.auth.api.dto.AuthResponse;
import com.ronkadosh.bubbleup.auth.api.dto.LoginRequest;
import com.ronkadosh.bubbleup.auth.api.dto.LogoutRequest;
import com.ronkadosh.bubbleup.auth.api.dto.RefreshRequest;
import com.ronkadosh.bubbleup.auth.api.dto.RegisterRequest;
import com.ronkadosh.bubbleup.auth.internal.dto.UserIdentity;
import com.ronkadosh.bubbleup.auth.model.RefreshToken;
import com.ronkadosh.bubbleup.auth.model.User;
import com.ronkadosh.bubbleup.auth.model.UserStatus;
import com.ronkadosh.bubbleup.auth.persistence.UserRepository;
import com.ronkadosh.bubbleup.common.context.UserRole;
import com.ronkadosh.bubbleup.common.error.AppException;
import com.ronkadosh.bubbleup.common.error.ErrorCode;
import com.ronkadosh.bubbleup.common.security.JwtService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
@RequiredArgsConstructor
public class AuthCommandService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final RefreshTokenService refreshTokenService;

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        if (userRepository.existsByEmail(request.email())) {
            throw new AppException(ErrorCode.EMAIL_ALREADY_EXISTS);
        }
        String displayName = request.displayName().trim();
        User user = User.builder()
                .email(request.email())
                .passwordHash(passwordEncoder.encode(request.password()))
                .role(UserRole.STUDENT)
                .displayName(displayName)
                // Password/testing accounts (dev-only /login/testing flow) are
                // trusted as verified — there is no separate email-verification step.
                .emailVerified(true)
                .build();
        userRepository.save(user);
        return issuePair(user);
    }

    @Transactional
    public AuthResponse login(LoginRequest request) {
        User user = userRepository.findByEmail(request.email())
                .orElseThrow(() -> new AppException(ErrorCode.INVALID_CREDENTIALS));
        requireAccountActive(user);
        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new AppException(ErrorCode.INVALID_CREDENTIALS);
        }
        return issuePair(user);
    }

    @Transactional
    public AuthResponse refresh(RefreshRequest request) {
        RefreshToken consumed = refreshTokenService.consume(request.refreshToken());
        User user = userRepository.findById(consumed.getUserId())
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));
        requireAccountActive(user);
        RefreshTokenService.IssuedRefresh next = refreshTokenService.issue(user.getId(), consumed.getId());
        String accessToken = jwtService.generateToken(user.getId(), user.getEmail(), user.getRole());
        return buildAuthResponse(accessToken, next.rawToken(), user);
    }

    @Transactional
    public void logout(LogoutRequest request) {
        refreshTokenService.revokeLeaf(request.refreshToken());
    }

    private AuthResponse issuePair(User user) {
        String accessToken = jwtService.generateToken(user.getId(), user.getEmail(), user.getRole());
        RefreshTokenService.IssuedRefresh refresh = refreshTokenService.issue(user.getId(), null);
        return buildAuthResponse(accessToken, refresh.rawToken(), user);
    }

    private void requireAccountActive(User user) {
        if (user.getStatus() == UserStatus.BANNED) {
            throw new AppException(ErrorCode.ACCOUNT_BANNED);
        }
        if (user.getStatus() == UserStatus.SUSPENDED) {
            Instant until = user.getSuspendedUntil();
            if (until == null || until.isAfter(Instant.now())) {
                throw new AppException(ErrorCode.ACCOUNT_SUSPENDED);
            }
            user.setStatus(UserStatus.ACTIVE);
            user.setSuspendedUntil(null);
            user.setStatusReason(null);
            userRepository.save(user);
        }
    }

    private static AuthResponse buildAuthResponse(String accessToken, String refreshToken, User user) {
        UserIdentity identity = new UserIdentity(user.getId(), user.getDisplayName(), user.getAvatarFileId());
        // Temporary password-based testing accounts should remain usable even
        // while the main Google flow requires Bubble.up email verification.
        boolean emailVerified = user.getGoogleSub() == null || user.isEmailVerified();
        return new AuthResponse(
                accessToken,
                refreshToken,
                user.getId(),
                user.getEmail(),
                user.getRole().name(),
                user.getDisplayName(),
                identity.avatarUrl(),
                emailVerified
        );
    }
}
