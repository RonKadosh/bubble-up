package com.ronkadosh.bubbleup.groups.api.dto;

import com.ronkadosh.bubbleup.groups.model.GroupVisibility;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.UUID;

/**
 * Create a Bubble against either a specific offering ({@code offeringId}) or a
 * course ({@code courseId}, resolved server-side to the current term's offering).
 * Exactly one must be set. The course-first form is what the hub UI uses — the
 * course picker doesn't know about terms; the offering-first form is what
 * integration tests and any future calendar-aware client use.
 */
public record CreateGroupRequest(
        @NotBlank @Size(max = 100) String name,
        @Size(max = 500) String description,
        GroupVisibility visibility,
        UUID offeringId,
        UUID courseId
) {
    @AssertTrue(message = "exactly one of offeringId or courseId must be provided")
    public boolean isExactlyOneTargetSet() {
        return (offeringId == null) ^ (courseId == null);
    }
}
