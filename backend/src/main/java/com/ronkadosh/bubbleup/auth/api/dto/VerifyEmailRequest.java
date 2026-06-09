package com.ronkadosh.bubbleup.auth.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Body of {@code POST /api/auth/verify-email/confirm}. The caller posts
 * the token they pulled from the verification email link. No auth header
 * required — the token itself is the proof.
 */
public record VerifyEmailRequest(
        @NotBlank @Size(min = 16, max = 128) String token
) {}
