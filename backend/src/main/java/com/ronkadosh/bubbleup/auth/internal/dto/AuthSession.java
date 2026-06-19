package com.ronkadosh.bubbleup.auth.internal.dto;

import java.util.UUID;

/**
 * A freshly minted login session (access + refresh token) for an existing user,
 * issued without credentials. Mirrors the fields of the public {@code AuthResponse}
 * so a caller can build the same wire shape the frontend expects.
 *
 * <p>Used by the demo module to auto-log-in the per-session guest user. The only
 * sanctioned no-credential token path — do not add others without a security review.
 */
public record AuthSession(
        String accessToken,
        String refreshToken,
        UUID userId,
        String email,
        String role,
        String displayName,
        String avatarUrl,
        boolean emailVerified
) {}
