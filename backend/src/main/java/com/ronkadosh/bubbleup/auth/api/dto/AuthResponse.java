package com.ronkadosh.bubbleup.auth.api.dto;

import java.util.UUID;

public record AuthResponse(
        String accessToken,
        String refreshToken,
        UUID userId,
        String email,
        String role,
        String displayName,
        String avatarUrl
) {}
