package com.ronkadosh.bubbleup.groups.api.dto;

import java.util.UUID;

/**
 * A user the owner can add to a Bubble: someone enrolled in the Bubble's offering
 * who isn't already a member. Backs the offering-scoped member search that
 * replaced the raw paste-a-UUID box. Cheap identity slice only (name + avatar) —
 * no affiliation / bio (the owner shares the offering, not necessarily a group).
 */
public record GroupCandidateResponse(
        UUID userId,
        String displayName,
        String avatarUrl
) {}
