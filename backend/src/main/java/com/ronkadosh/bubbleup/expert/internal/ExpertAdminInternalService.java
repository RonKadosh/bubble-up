package com.ronkadosh.bubbleup.expert.internal;

import com.ronkadosh.bubbleup.expert.internal.dto.admin.ExpertAdminProfileDto;
import com.ronkadosh.bubbleup.expert.model.VerificationStatus;

import java.util.List;
import java.util.UUID;

/**
 * Admin-only surface for expert verification. Reject deletes the profile entirely
 * and triggers a role demotion (handled by the admin service via
 * {@code AuthInternalService.demoteToStudent}); revoke clears verification flags
 * but keeps the profile + EXPERT role intact.
 */
public interface ExpertAdminInternalService {

    List<ExpertAdminProfileDto> listByStatus(VerificationStatus status);

    /** Throws {@code EXPERT_PROFILE_NOT_FOUND} if no profile exists for {@code userId}. */
    ExpertAdminProfileDto getByUser(UUID userId);

    /**
     * Sets the profile to VERIFIED, stamping {@code verifiedAt = now} and
     * {@code verifiedBy = adminUserId}. Idempotent on already-verified profiles
     * (no-op, returns current). Throws {@code ADMIN_EXPERT_PROFILE_NOT_PENDING}
     * if the profile is in REJECTED state (shouldn't happen since reject deletes).
     */
    ExpertAdminProfileDto verify(UUID userId, UUID adminUserId);

    /**
     * Clears the verified flag + verifiedAt + verifiedBy, returning the profile
     * to PENDING. Keeps the profile row; the admin service follows up with
     * {@code AuthInternalService.demoteToStudent(userId)} to pull the EXPERT role.
     */
    ExpertAdminProfileDto revoke(UUID userId);

    /**
     * Deletes the profile entirely. Caller (admin service) follows up with
     * {@code AuthInternalService.demoteToStudent(userId)}.
     */
    void rejectAndDeleteProfile(UUID userId);

    long countVerified();

    long countByStatus(VerificationStatus status);
}
