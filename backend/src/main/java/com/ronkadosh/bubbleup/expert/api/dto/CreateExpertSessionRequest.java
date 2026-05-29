package com.ronkadosh.bubbleup.expert.api.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;

public record CreateExpertSessionRequest(
        @NotBlank @Size(max = 140) String title,
        @Size(max = 2000) String description,
        @NotNull Instant startsAt,
        @NotNull Instant endsAt,
        @Min(1) @Max(50) int capacity
) {}
