package com.ronkadosh.bubbleup.groups.internal.dto;

import java.util.UUID;

/**
 * Cross-module summary of a study group. {@code courseId} is resolved from the
 * group's offering at population time so callers (notably matching/) can keep
 * clustering by course without knowing about offerings.
 */
public record GroupSummary(
        UUID id,
        String name,
        UUID offeringId,
        UUID courseId,
        int memberCount
) {}
