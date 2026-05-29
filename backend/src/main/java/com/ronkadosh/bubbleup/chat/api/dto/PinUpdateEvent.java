package com.ronkadosh.bubbleup.chat.api.dto;

import java.time.Instant;
import java.util.UUID;

/**
 * Live update broadcast on {@code /topic/chat/{roomId}/pins} when a message is pinned or unpinned.
 * Fields {@code pinnedAt} / {@code pinnedByUserId} are null on an unpin event.
 */
public record PinUpdateEvent(
        UUID messageId,
        boolean pinned,
        Instant pinnedAt,
        UUID pinnedByUserId
) {
}
