package com.ronkadosh.bubbleup.auth.application;

import com.ronkadosh.bubbleup.auth.model.RefreshToken;
import com.ronkadosh.bubbleup.auth.persistence.RefreshTokenRepository;
import com.ronkadosh.bubbleup.common.config.SecurityProperties;
import com.ronkadosh.bubbleup.common.datetime.TimeProvider;
import com.ronkadosh.bubbleup.common.error.AppException;
import com.ronkadosh.bubbleup.common.error.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class RefreshTokenService {

    private static final SecureRandom RNG = new SecureRandom();
    private static final int RAW_BYTES = 32;

    private final RefreshTokenRepository repo;
    private final SecurityProperties props;
    private final TimeProvider timeProvider;
    private final RefreshTokenChainRevoker chainRevoker;

    public record IssuedRefresh(String rawToken, RefreshToken persisted) {}

    @Transactional
    public IssuedRefresh issue(UUID userId, UUID rotatedFromId) {
        byte[] raw = new byte[RAW_BYTES];
        RNG.nextBytes(raw);
        String rawToken = Base64.getUrlEncoder().withoutPadding().encodeToString(raw);
        Instant now = timeProvider.now();
        RefreshToken row = repo.save(RefreshToken.builder()
                .tokenHash(sha256Hex(rawToken))
                .userId(userId)
                .expiresAt(now.plusMillis(props.refreshTokenExpirationMs()))
                .rotatedFromId(rotatedFromId)
                .build());
        return new IssuedRefresh(rawToken, row);
    }

    /**
     * Looks up the row by hash. If revoked already → revoke the entire chain in a
     * REQUIRES_NEW transaction (so it commits independently of this method's rollback)
     * and throw REFRESH_TOKEN_REUSED. If expired → REFRESH_TOKEN_EXPIRED. Otherwise
     * mark this row revokedAt = now and return it.
     */
    @Transactional
    public RefreshToken consume(String rawToken) {
        RefreshToken row = repo.findByTokenHash(sha256Hex(rawToken))
                .orElseThrow(() -> new AppException(ErrorCode.INVALID_REFRESH_TOKEN));
        if (row.getRevokedAt() != null) {
            chainRevoker.revokeChain(row.getId());
            throw new AppException(ErrorCode.REFRESH_TOKEN_REUSED);
        }
        Instant now = timeProvider.now();
        if (row.getExpiresAt().isBefore(now)) {
            throw new AppException(ErrorCode.REFRESH_TOKEN_EXPIRED);
        }
        row.setRevokedAt(now);
        repo.save(row);
        return row;
    }

    /**
     * Best-effort logout: revoke the row if it exists. Silent on miss to avoid
     * token-existence probing.
     */
    @Transactional
    public void revokeLeaf(String rawToken) {
        Optional<RefreshToken> row = repo.findByTokenHash(sha256Hex(rawToken));
        row.ifPresent(r -> {
            if (r.getRevokedAt() == null) {
                r.setRevokedAt(timeProvider.now());
                repo.save(r);
            }
        });
    }

    private static String sha256Hex(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(digest.length * 2);
            for (byte b : digest) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
