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
import lombok.extern.slf4j.Slf4j;
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
 *       itself verified ({@code email_verified=true}).</li>
 *   <li>The email's domain must resolve to a registered Israeli academic
 *       institution via {@link UniversityEmailRegistry}. Non-{@code .ac.il}
 *       Google accounts are rejected with {@link ErrorCode#NOT_ACADEMIC_EMAIL}
 *       — they cannot sign up through this flow at all.</li>
 *   <li>That is the whole gate: Google has already verified the address and
 *       the academic-domain check is the "uni only" rule, so a first-time
 *       sign-up is created already verified and goes straight into the app.
 *       There is no separate Bubble.up email-verification step (the earlier
 *       SES-link flow was dropped).</li>
 * </ol>
 *
 * <p>Result: the OAuth flow accepts only Israeli academic Google accounts and
 * activates them immediately. Testing / QA can still use the password
 * endpoints behind {@code /login/testing}; that path is handled separately.
 */
@Service
@RequiredArgsConstructor
@Slf4j
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
        Match academicMatch = universityRegistry.lookup(normalisedEmail)
                .orElseThrow(() -> {
                    log.info("OAuth academic-domain check failed for Google email domain={}",
                            emailDomain(normalisedEmail));
                    return new AppException(ErrorCode.NOT_ACADEMIC_EMAIL,
                            "Sign-in requires an Israeli academic email address (.ac.il).");
                });

        // 1) Stable sub lookup first — survives email changes on the Google side.
        Optional<User> existingBySub = userRepository.findByGoogleSub(googleSub);
        if (existingBySub.isPresent()) {
            User user = existingBySub.get();
            requireAccountActive(user);
            return issuePair(user);
        }

        // 2) Email-based fallback for the legacy / migration window.
        Optional<User> existingByEmail = userRepository.findByEmail(normalisedEmail);
        if (existingByEmail.isPresent()) {
            User user = existingByEmail.get();
            requireAccountActive(user);
            user.setGoogleSub(googleSub);
            return issuePair(user);
        }

        // 3) Brand new user. Google has already verified the address
        //    (email_verified, checked above) and the academic-domain gate is
        //    the "uni only" rule, so the account is active immediately — there
        //    is no separate Bubble.up email-verification step (SES was dropped).
        UserRole defaultRole = academicMatch.kind() == MemberKind.STAFF
                ? UserRole.EXPERT
                : UserRole.STUDENT;

        User user = User.builder()
                .email(normalisedEmail)
                .googleSub(googleSub)
                .emailVerified(true)
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

    private static String emailDomain(String email) {
        int at = email.indexOf('@');
        return at > 0 && at < email.length() - 1 ? email.substring(at + 1) : "(missing)";
    }
}
