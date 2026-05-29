package com.ronkadosh.bubbleup.expert.internal.dto;

import com.ronkadosh.bubbleup.expert.model.VerificationStatus;

import java.util.Set;
import java.util.UUID;

public record ExpertProfileSummary(
        UUID id,
        UUID userId,
        String headline,
        Set<String> expertiseTags,
        VerificationStatus verificationStatus
) {}
