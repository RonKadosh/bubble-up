package com.ronkadosh.bubbleup.expert.api.dto;

import java.util.Set;
import java.util.UUID;

/**
 * Broadcast on {@code /topic/expert-sessions/{sessionId}/writers} whenever the
 * host grants or revokes whiteboard write access. {@code writers} is the full
 * authoritative set after the change (not a delta) — the host is implicitly a
 * writer and may not appear in this list.
 */
public record WhiteboardWritersEvent(
        UUID sessionId,
        Set<UUID> writers
) {}
