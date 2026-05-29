package com.ronkadosh.bubbleup.auth.api.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.UUID;

/**
 * Patch payload for {@code PATCH /api/users/me/profile}. All fields are optional —
 * absent fields leave the existing value alone. Sending {@code null} explicitly
 * for {@code universityId} or {@code departmentId} clears affiliation.
 *
 * <p>To distinguish "absent" from "null", we use {@link java.util.Optional} on the
 * wire is not idiomatic in JSON; instead we treat {@code null} on the record as
 * "no change" (the common case) and require a dedicated {@code clearAffiliation}
 * flag if the caller wants to unset. For v1 the UI never clears affiliation, so
 * this is a non-issue — the flag is omitted.
 *
 * <p>{@code displayName} regex blocks C0/C1 control chars and Unicode format
 * chars (zero-width joiners, BiDi overrides). Hebrew / Arabic / CJK / emoji
 * all pass. The service trims before persisting; {@code @Size(min=1)} ensures
 * a non-empty value when present.
 */
public record UpdateProfileRequest(
        @Pattern(regexp = "^[^\\p{Cntrl}\\p{Cc}\\p{Cf}]+$",
                 message = "displayName contains control characters")
        @Size(min = 1, max = 100)
        String displayName,

        @Size(max = 280)
        String bio,

        UUID universityId,
        UUID departmentId,
        @Min(value = 1, message = "enrollmentYear must be between 1 and 10")
        @Max(value = 10, message = "enrollmentYear must be between 1 and 10")
        Integer enrollmentYear
) {}
