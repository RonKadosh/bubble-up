package com.ronkadosh.studybuddy.auth.application;

import com.ronkadosh.studybuddy.auth.api.dto.AuthResponse;
import com.ronkadosh.studybuddy.auth.api.dto.LoginRequest;
import com.ronkadosh.studybuddy.auth.api.dto.LogoutRequest;
import com.ronkadosh.studybuddy.auth.api.dto.RefreshRequest;
import com.ronkadosh.studybuddy.auth.api.dto.RegisterRequest;
import com.ronkadosh.studybuddy.auth.model.RefreshToken;
import com.ronkadosh.studybuddy.auth.model.User;
import com.ronkadosh.studybuddy.auth.persistence.UserRepository;
import com.ronkadosh.studybuddy.common.context.UserRole;
import com.ronkadosh.studybuddy.common.error.AppException;
import com.ronkadosh.studybuddy.common.error.ErrorCode;
import com.ronkadosh.studybuddy.common.security.JwtService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final RefreshTokenService refreshTokenService;

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        if (userRepository.existsByEmail(request.email())) {
            throw new AppException(ErrorCode.EMAIL_ALREADY_EXISTS);
        }
        User user = User.builder()
                .email(request.email())
                .passwordHash(passwordEncoder.encode(request.password()))
                .role(UserRole.STUDENT)
                .build();
        userRepository.save(user);
        return issuePair(user);
    }

    @Transactional
    public AuthResponse login(LoginRequest request) {
        User user = userRepository.findByEmail(request.email())
                .orElseThrow(() -> new AppException(ErrorCode.INVALID_CREDENTIALS));
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
        RefreshTokenService.IssuedRefresh next = refreshTokenService.issue(user.getId(), consumed.getId());
        String accessToken = jwtService.generateToken(user.getId(), user.getEmail(), user.getRole());
        return new AuthResponse(
                accessToken,
                next.rawToken(),
                user.getId(),
                user.getEmail(),
                user.getRole().name()
        );
    }

    @Transactional
    public void logout(LogoutRequest request) {
        refreshTokenService.revokeLeaf(request.refreshToken());
    }

    private AuthResponse issuePair(User user) {
        String accessToken = jwtService.generateToken(user.getId(), user.getEmail(), user.getRole());
        RefreshTokenService.IssuedRefresh refresh = refreshTokenService.issue(user.getId(), null);
        return new AuthResponse(
                accessToken,
                refresh.rawToken(),
                user.getId(),
                user.getEmail(),
                user.getRole().name()
        );
    }
}
