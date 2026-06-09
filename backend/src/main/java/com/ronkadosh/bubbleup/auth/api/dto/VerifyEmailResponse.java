package com.ronkadosh.bubbleup.auth.api.dto;

/**
 * Returned from {@code POST /api/auth/verify-email/confirm}. The user
 * already had a session JWT before clicking the link; we just confirm the
 * updated state so the frontend can move them off the verification page.
 */
public record VerifyEmailResponse(
        boolean emailVerified,
        String email
) {}
