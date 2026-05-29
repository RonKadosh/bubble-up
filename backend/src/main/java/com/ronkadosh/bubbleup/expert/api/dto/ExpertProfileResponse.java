package com.ronkadosh.bubbleup.expert.api.dto;

import com.ronkadosh.bubbleup.expert.model.ExpertProfile;
import com.ronkadosh.bubbleup.expert.model.VerificationStatus;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

public record ExpertProfileResponse(
        UUID id,
        UUID userId,
        String headline,
        String bio,
        Set<String> expertiseTags,
        VerificationStatus verificationStatus,
        Instant verifiedAt,
        Instant appliedAt
) {
    public static ExpertProfileResponse from(ExpertProfile p) {
        return new ExpertProfileResponse(
                p.getId(),
                p.getUserId(),
                p.getHeadline(),
                p.getBio(),
                Set.copyOf(p.getExpertiseTags()),
                p.getVerificationStatus(),
                p.getVerifiedAt(),
                p.getAppliedAt()
        );
    }
}
