package com.ronkadosh.bubbleup.onboarding.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Marks one namespaced onboarding key as acknowledged (e.g. {@code explainer:whatIsBubble}, {@code guide:enroll}). */
public record AckRequest(
        @NotBlank @Size(max = 64) String key
) {}
