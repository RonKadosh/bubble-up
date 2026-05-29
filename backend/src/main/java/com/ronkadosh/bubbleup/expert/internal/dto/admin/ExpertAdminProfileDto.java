package com.ronkadosh.bubbleup.expert.internal.dto.admin;

import com.ronkadosh.bubbleup.expert.model.VerificationStatus;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

public record ExpertAdminProfileDto(
        UUID id,
        UUID userId,
        String headline,
        String bio,
        Set<String> expertiseTags,
        VerificationStatus verificationStatus,
        Instant verifiedAt,
        UUID verifiedBy,
        Instant appliedAt
) {}
