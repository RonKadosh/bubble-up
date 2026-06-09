package com.ronkadosh.bubbleup.auth.application;

import com.ronkadosh.bubbleup.auth.api.dto.AuthResponse;
import com.ronkadosh.bubbleup.auth.application.UniversityEmailRegistry.Match;
import com.ronkadosh.bubbleup.auth.application.UniversityEmailRegistry.MemberKind;
import com.ronkadosh.bubbleup.auth.internal.dto.UserIdentity;
import com.ronkadosh.bubbleup.auth.model.User;
import com.ronkadosh.bubbleup.auth.model.UserStatus;
import com.ronkadosh.bubbleup.auth.persistence.UserRepository;
import com.ronkadosh.bubbleup.common.context.UserRole;
import com.ronkadosh.bubbleup.common.error.AppException;
import com.ronkadosh.bubbleup.common.error.ErrorCode;
import com.ronkadosh.bubbleup.common.security.JwtService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Locale;
import java.util.Optional;

/**
 * Find-or-create + JWT issuance for users arriving via Google OAuth2.
 *
 * <h2>Sign-up policy</h2>
 *
 * <ol>
 *   <li>The Google ID token must carry an {@code email} claim that Google
 *       has verified ({@code email_verified=true}).</li>
 *   <li>If the email itself resolves to a registered Israeli academic
 *       institution (see {@link UniversityEmailRegistry}) the user is
 *       <b>fully signed in</b>: {@code emailVerified=true}, role inferred
 *       from the matched sub-domain.</li>
 *   <li>If the email is a non-{@code .ac.il} Google account (e.g. a
 *       personal Gmail) the user is created in a <b>pending</b> state:
 *       {@code emailVerified=false}, role defaults to STUDENT. They keep
 *       a session JWT but the frontend routes them to the
 *       {@code /auth/verify} page, where they enter their academic email
 *       and prove ownership via {@link EmailVerificationService}.</li>
 * </ol>
 *
 * <p>Result: every user who can ever read app data has a verified
 * {@code .ac.il} address, but BGU / HUJI / Bar-Ilan students (who use
 * Microsoft 365 for student mail and so can't sign in to Google with
 * their academic address) still have a smooth path in.
 */
@Service
@RequiredArgsConstructor
public class OAuthLoginService {

    private final UserRepository userRepository;
    private final JwtService jwtService;
    private final RefreshTokenService refreshTokenService;
    private final UniversityEmailRegistry universityRegistry;

    /**
     * Process a verified Google OAuth2 callback and return our own
     * access+refresh tokens for the corresponding (existing or newly created)
     * user.
     */
    @Transactional
    public AuthResponse loginOrRegister(
            String googleSub,
            String email,
            boolean emailVerified,
            String displayName
    ) {
        if (email == null || email.isBlank()) {
            throw new AppException(ErrorCode.OAUTH_EMAIL_MISSING);
        }
        if (!emailVerified) {
            throw new AppException(ErrorCode.OAUTH_EMAIL_UNVERIFIED);
        }

        String normalisedEmail = email.toLowerCase(Locale.ROOT).trim();
        Optional<Match> academicMatch = universityRegistry.lookup(normalisedEmail);

        // 1) Stable sub lookup first — survives email changes on the Google side.
        Optional<User> existingBySub = userRepository.findByGoogleSub(googleSub);
        if (existingBySub.isPresent()) {
            User user = existingBySub.get();
            requireAccountActive(user);
            // If their (still-stored) email turned out to be academic now,
            // promote them. Otherwise leave verification state alone.
            if (universityRegistry.isAcademicEmail(user.getEmail())) {
                user.setEmailVerified(true);
            }
            return issuePair(user);
        }

        // 2) Email-based fallback for the legacy / migration window.
        Optional<User> existingByEmail = userRepository.findByEmail(normalisedEmail);
        if (existingByEmail.isPresent()) {
            User user = existingByEmail.get();
            requireAccountActive(user);
            user.setGoogleSub(googleSub);
            if (academicMatch.isPresent()) {
                user.setEmailVerified(true);
            }
            return issuePair(user);
        }

        // 3) Brand new user.
        UserRole defaultRole = academicMatch
                .filter(m -> m.kind() == MemberKind.STAFF)
                .map(m -> UserRole.EXPERT)
                .orElse(UserRole.STUDENT);

        User user = User.builder()
                .email(normalisedEmail)
                .googleSub(googleSub)
                .emailVerified(academicMatch.isPresent())   // academic Google email = trust Google
                .role(defaultRole)
                .displayName(safeName(displayName, normalisedEmail))
                .build();
        userRepository.save(user);
        return issuePair(user);
    }

    private AuthResponse issuePair(User user) {
        String accessToken = jwtService.generateToken(user.getId(), user.getEmail(), user.getRole());
        RefreshTokenService.IssuedRefresh refresh = refreshTokenService.issue(user.getId(), null);
        UserIdentity identity = new UserIdentity(user.getId(), user.getDisplayName(), user.getAvatarFileId());
        return new AuthResponse(
                accessToken,
                refresh.rawToken(),
                user.getId(),
                user.getEmail(),
                user.getRole().name(),
                user.getDisplayName(),
                identity.avatarUrl(),
                user.isEmailVerified()
        );
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

    /**
     * Choose a display name. Google usually provides one; if not, fall back
     * to the email local-part (e.g. "joe.smith" from "joe.smith@bgu.ac.il").
     */
    private static String safeName(String fromGoogle, String email) {
        if (fromGoogle != null && !fromGoogle.isBlank()) {
            String trimmed = fromGoogle.trim();
            return trimmed.length() > 100 ? trimmed.substring(0, 100) : trimmed;
        }
        int at = email.indexOf('@');
        return at > 0 ? email.substring(0, at) : email;
    }
}
