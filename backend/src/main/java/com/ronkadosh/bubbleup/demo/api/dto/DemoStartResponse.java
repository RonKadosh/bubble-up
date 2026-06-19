package com.ronkadosh.bubbleup.demo.api.dto;

import java.util.UUID;

/**
 * Response to {@code POST /api/demo/start}: the freshly-minted guest session plus
 * the bits the frontend needs to drop straight into the hub and launch the tour.
 *
 * <p>{@code user} mirrors the shape {@code authStore.setAuth} expects; the
 * frontend calls {@code setAuth(accessToken, refreshToken, user)} then navigates
 * to the hub with the tour running, anchored on {@code starterGroupId}.
 */
public record DemoStartResponse(
        String accessToken,
        String refreshToken,
        DemoUser user,
        UUID starterGroupId,
        boolean startTour
) {
    public record DemoUser(
            UUID id,
            String email,
            String role,
            String displayName,
            String avatarUrl,
            boolean emailVerified
    ) {}
}
