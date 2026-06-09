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
 * <p>This service is the only place that owns the business rules around
 * "what makes a Google sign-in acceptable" — currently:
 *
 * <ol>
 *   <li>The Google ID token must carry an {@code email} claim.</li>
 *   <li>Google must have verified the email ({@code email_verified=true}).</li>
 *   <li>The email's domain must resolve to a registered Israeli academic
 *       institution (see {@link UniversityEmailRegistry}).</li>
 * </ol>
 *
 * <p>If all three pass, we look the user up by Google's stable {@code sub}
 * identifier (preferred — survives email changes) and fall back to email.
 * A brand-new user is created on the fly. Either way we return a fresh
 * access + refresh token pair, identical to what the legacy password
 * {@code /api/auth/login} returned, so the frontend's existing JWT handling
 * keeps working without changes.
 *
 * <p>This service does NOT touch HTTP. The Spring Security
 * {@code AuthenticationSuccessHandler} is responsible for translating its
 * return value into a redirect to the frontend. Keeping HTTP out of here
 * makes it cleanly testable without a mock servlet.
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
     *
     * @param googleSub      Google's stable subject identifier (the {@code sub} claim)
     * @param email          email address from the ID token
     * @param emailVerified  Google's {@code email_verified} claim
     * @param displayName    name from Google's profile, or null
     * @throws AppException with a domain-specific {@link ErrorCode} when any
     *                      gate above fails. The success handler converts the
     *                      code into a frontend-friendly query parameter so
     *                      the UI can render a sensible message.
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
        Match match = universityRegistry.lookup(email)
                .orElseThrow(() -> new AppException(ErrorCode.NOT_ACADEMIC_EMAIL,
                        "Sign-in requires an Israeli academic email address (.ac.il)."));

        String normalisedEmail = email.toLowerCase(Locale.ROOT).trim();

        // 1) Stable sub lookup first — survives email changes on the Google side.
        //    NOTE: we deliberately don't update the stored email here even if
        //    the user changed it on Google. The email is treated as immutable
        //    after first sign-up; a future "change email" endpoint will handle
        //    that case explicitly with its own re-verification flow.
        Optional<User> existingBySub = userRepository.findByGoogleSub(googleSub);
        if (existingBySub.isPresent()) {
            User user = existingBySub.get();
            requireAccountActive(user);
            user.setEmailVerified(true);
            return issuePair(user);
        }

        // 2) Fall back to email — covers the migration case where a legacy
        //    password user exists but hasn't yet linked their Google identity.
        Optional<User> existingByEmail = userRepository.findByEmail(normalisedEmail);
        if (existingByEmail.isPresent()) {
            User user = existingByEmail.get();
            requireAccountActive(user);
            user.setGoogleSub(googleSub);
            user.setEmailVerified(true);
            return issuePair(user);
        }

        // 3) Brand new user — create row.
        User user = User.builder()
                .email(normalisedEmail)
                .googleSub(googleSub)
                .emailVerified(true)
                .role(match.kind() == MemberKind.STAFF ? UserRole.EXPERT : UserRole.STUDENT)
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
                identity.avatarUrl()
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
