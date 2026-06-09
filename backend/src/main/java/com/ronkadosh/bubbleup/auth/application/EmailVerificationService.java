package com.ronkadosh.bubbleup.auth.application;

import com.ronkadosh.bubbleup.auth.application.UniversityEmailRegistry.Match;
import com.ronkadosh.bubbleup.auth.model.EmailVerificationToken;
import com.ronkadosh.bubbleup.auth.model.User;
import com.ronkadosh.bubbleup.auth.persistence.EmailVerificationTokenRepository;
import com.ronkadosh.bubbleup.auth.persistence.UserRepository;
import com.ronkadosh.bubbleup.common.config.MailProperties;
import com.ronkadosh.bubbleup.common.error.AppException;
import com.ronkadosh.bubbleup.common.error.ErrorCode;
import com.ronkadosh.bubbleup.common.mail.EmailSender;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Locale;
import java.util.UUID;

/**
 * Issues + redeems "prove you own this {@code .ac.il} address" verification
 * tokens. The flow exists for users who signed in with a personal Google
 * account (e.g. Gmail) and want to claim a university identity — typical
 * for BGU/HUJI/Bar-Ilan students whose academic mailbox is Microsoft 365
 * (so they can't directly sign in to Google with it).
 *
 * <p>Threat model:
 * <ul>
 *   <li>Email-only addresses can't act as a privilege escalation channel —
 *       we only flip {@code emailVerified} + update {@code email}, never
 *       grant ADMIN.</li>
 *   <li>Tokens are 32 random bytes (256 bits, base64url). We store only
 *       SHA-256; a DB read can't forge a valid link.</li>
 *   <li>Single-use: {@code usedAt} pinned on redemption, plus issuing a
 *       new token invalidates all live ones for the user.</li>
 *   <li>Rate limited at 5 emails per user per hour to keep SES costs and
 *       inbox spam in check.</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class EmailVerificationService {

    private static final int TOKEN_BYTES = 32;
    /** Cap requests per user per hour. Bypassed only by tests. */
    private static final int MAX_REQUESTS_PER_HOUR = 5;

    private final EmailVerificationTokenRepository tokenRepository;
    private final UserRepository userRepository;
    private final UniversityEmailRegistry universityRegistry;
    private final EmailSender emailSender;
    private final MailProperties mailProperties;
    private final SecureRandom random = new SecureRandom();

    /**
     * Generate + email a verification link for {@code academicEmail}. The
     * link points at {@code <frontendBaseUrl>/auth/verify?token=...}.
     *
     * @throws AppException EMAIL_ALREADY_VERIFIED if the user is already verified
     *                      against this address.
     * @throws AppException NOT_ACADEMIC_EMAIL if the address isn't registered.
     * @throws AppException VERIFICATION_TOO_MANY_REQUESTS at >5 / hour / user.
     * @throws AppException MAIL_NOT_CONFIGURED in environments without SES set up.
     */
    @Transactional
    public void requestVerification(UUID userId, String academicEmail) {
        if (academicEmail == null || academicEmail.isBlank()) {
            throw new AppException(ErrorCode.VALIDATION_ERROR, "Academic email is required.");
        }
        String normalised = academicEmail.toLowerCase(Locale.ROOT).trim();

        Match match = universityRegistry.lookup(normalised)
                .orElseThrow(() -> new AppException(ErrorCode.NOT_ACADEMIC_EMAIL,
                        "That doesn't look like an Israeli academic email (.ac.il)."));

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));

        if (user.isEmailVerified() && user.getEmail().equalsIgnoreCase(normalised)) {
            throw new AppException(ErrorCode.EMAIL_ALREADY_VERIFIED,
                    "This email is already verified.");
        }

        Instant now = Instant.now();
        long recent = tokenRepository.countRecentRequests(userId, now.minus(Duration.ofHours(1)));
        if (recent >= MAX_REQUESTS_PER_HOUR) {
            throw new AppException(ErrorCode.VERIFICATION_TOO_MANY_REQUESTS,
                    "Too many verification requests. Please try again later.");
        }

        // Invalidate any earlier live tokens for this user — only the newest
        // link should keep working.
        tokenRepository.invalidateLiveTokensForUser(userId, now);

        // Mint a fresh token; the raw value is mailed, only its hash persists.
        String rawToken = randomToken();
        String hash = sha256Hex(rawToken);
        Duration ttl = mailProperties.verificationTtl() != null
                ? mailProperties.verificationTtl()
                : Duration.ofMinutes(30);

        tokenRepository.save(EmailVerificationToken.builder()
                .userId(userId)
                .tokenHash(hash)
                .requestedEmail(normalised)
                .createdAt(now)
                .expiresAt(now.plus(ttl))
                .build());

        sendEmail(normalised, match.displayName(), rawToken, user.getDisplayName());
        log.info("Verification email sent to {} for user {}", normalised, userId);
    }

    /**
     * Redeem a verification link. On success the user's {@code email} is set
     * to the address that was verified and {@code emailVerified} flips true.
     *
     * @return the now-verified user (caller usually wants the updated email
     *         + verified flag for the response)
     */
    @Transactional
    public User redeem(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            throw new AppException(ErrorCode.VERIFICATION_TOKEN_INVALID);
        }
        String hash = sha256Hex(rawToken);

        EmailVerificationToken token = tokenRepository.findByTokenHash(hash)
                .orElseThrow(() -> new AppException(ErrorCode.VERIFICATION_TOKEN_INVALID));

        Instant now = Instant.now();
        if (token.getUsedAt() != null) {
            throw new AppException(ErrorCode.VERIFICATION_TOKEN_INVALID,
                    "This verification link has already been used.");
        }
        if (token.getExpiresAt().isBefore(now)) {
            throw new AppException(ErrorCode.VERIFICATION_TOKEN_EXPIRED);
        }

        token.setUsedAt(now);

        User user = userRepository.findById(token.getUserId())
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));

        // Promote the verified academic email to the user's primary email.
        user.verifyEmail(token.getRequestedEmail());
        return user;
    }

    // ─── Helpers ──────────────────────────────────────────────────────────

    private String randomToken() {
        byte[] bytes = new byte[TOKEN_BYTES];
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    /** Deterministic, no salt — the token itself has 256 bits of entropy. */
    private static String sha256Hex(String s) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(s.getBytes());
            StringBuilder hex = new StringBuilder(digest.length * 2);
            for (byte b : digest) hex.append(String.format("%02x", b));
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 missing from JRE", e);
        }
    }

    private void sendEmail(String to, String universityName, String rawToken, String displayName) {
        String link = mailProperties.frontendBaseUrl() + "/auth/verify?token=" + rawToken;
        String greeting = (displayName != null && !displayName.isBlank())
                ? "Hi " + displayName + ","
                : "Hi,";
        String subject = "Verify your " + universityName + " email for Bubble.up";
        String body = """
                %s

                You're one click away from finishing your Bubble.up sign-up.

                Click the link below within the next 30 minutes to confirm that this
                email address (%s) belongs to you:

                %s

                If you didn't try to sign up for Bubble.up, you can safely ignore this
                email — no account will be created.

                — Bubble.up
                """.formatted(greeting, to, link);
        emailSender.send(to, subject, body);
    }
}
