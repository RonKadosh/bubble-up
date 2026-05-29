package com.ronkadosh.bubbleup.admin.api.dto;

import com.ronkadosh.bubbleup.expert.internal.dto.admin.ExpertAdminProfileDto;
import com.ronkadosh.bubbleup.expert.model.VerificationStatus;
import jakarta.validation.constraints.NotBlank;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

public final class AdminExpertDtos {
    private AdminExpertDtos() {}

    public record ExpertResponse(
            UUID id,
            UUID userId,
            String headline,
            String bio,
            Set<String> expertiseTags,
            VerificationStatus verificationStatus,
            Instant verifiedAt,
            UUID verifiedBy,
            Instant appliedAt
    ) {
        public static ExpertResponse from(ExpertAdminProfileDto d) {
            return new ExpertResponse(
                    d.id(), d.userId(), d.headline(), d.bio(),
                    d.expertiseTags(), d.verificationStatus(),
                    d.verifiedAt(), d.verifiedBy(), d.appliedAt()
            );
        }
    }

    /** Reason optional on verify (admin notes only). */
    public record VerifyRequest(String reason) {}
    public record RejectRequest(@NotBlank String reason) {}
    public record RevokeRequest(@NotBlank String reason) {}
}
