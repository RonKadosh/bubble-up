package com.ronkadosh.bubbleup.common.events;

import java.util.UUID;

/**
 * A user joined a group via the self-join flow. Unlike {@link GroupMembershipChangedEvent}
 * (group-scoped, fired for any membership change) this carries BOTH the actor and the
 * group, so the matching-feedback module can correlate a join with a prior recommendation
 * impression. Published from {@code GroupCommandService.joinGroup}.
 */
public record GroupJoinedEvent(UUID userId, UUID groupId) {}
