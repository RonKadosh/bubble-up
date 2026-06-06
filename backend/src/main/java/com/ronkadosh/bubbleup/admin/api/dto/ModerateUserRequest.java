package com.ronkadosh.bubbleup.admin.api.dto;

import jakarta.validation.constraints.NotBlank;

import java.time.Instant;

public record ModerateUserRequest(
        Instant suspendedUntil,
        @NotBlank String reason
) {}
