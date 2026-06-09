package com.ronkadosh.bubbleup.auth.api.dto;

import java.util.UUID;

/**
 * Tokens + user summary returned from any auth flow. {@code emailVerified}
 * lets the frontend route the user to the academic-email verification
 * page when they signed in with a non-{@code .ac.il} Google account.
 */
public record AuthResponse(
        String accessToken,
        String refreshToken,
        UUID userId,
        String email,
        String role,
        String displayName,
        String avatarUrl,
        boolean emailVerified
) {}
