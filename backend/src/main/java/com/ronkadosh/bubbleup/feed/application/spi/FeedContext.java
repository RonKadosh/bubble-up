package com.ronkadosh.bubbleup.feed.application.spi;

import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Everything a {@link FeedSource} needs to do its work, assembled once per request
 * by {@link com.ronkadosh.bubbleup.feed.application.FeedQueryService}:
 * the caller, their group ids + names (so sources don't re-fetch them), the
 * current time, and a per-source fetch cap.
 */
public record FeedContext(
        UUID userId,
        Set<UUID> groupIds,
        Map<UUID, String> groupNames,
        Instant now,
        int fetchLimit
) {
    /** Convenience: resolve a group's display name, falling back to a placeholder. */
    public String groupName(UUID groupId) {
        return groupNames.getOrDefault(groupId, "");
    }
}
