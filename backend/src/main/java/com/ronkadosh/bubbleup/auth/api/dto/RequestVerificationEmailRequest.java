package com.ronkadosh.bubbleup.auth.api.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Body of {@code POST /api/auth/verify-email/request}. The caller is the
 * already-signed-in user (via JWT) — they're telling us which academic
 * address to send the verification link to.
 */
public record RequestVerificationEmailRequest(
        @NotBlank @Email @Size(max = 320) String academicEmail
) {}
