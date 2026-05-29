package com.ronkadosh.bubbleup.expert.api.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.UUID;

public record CreateBookingRequest(
        @NotNull UUID expertUserId,
        @NotNull UUID groupId,
        @NotNull Instant proposedStartsAt,
        @NotNull Instant proposedEndsAt,
        @Size(max = 500) String message
) {}
