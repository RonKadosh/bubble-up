package com.ronkadosh.bubbleup.onboarding.api.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

/** Sets the onboarding wizard's current level (1–5; 6 = finished). */
public record LevelRequest(
        @Min(1) @Max(6) int level
) {}
