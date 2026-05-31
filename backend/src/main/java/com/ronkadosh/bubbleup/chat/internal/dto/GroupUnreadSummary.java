package com.ronkadosh.bubbleup.chat.internal.dto;

import java.time.Instant;
import java.util.UUID;

/**
 * Per-group unread rollup for the current user: total unread across the group's
 * rooms, plus the timestamp of the newest message in those rooms (for feed
 * ordering). Only groups with {@code unreadCount > 0} are returned.
 */
public record GroupUnreadSummary(
        UUID groupId,
        int unreadCount,
        Instant latestMessageAt
) {}
