package com.ronkadosh.bubbleup.groups.api.dto;

import java.time.Instant;
import java.util.UUID;

/**
 * Per-user presence snapshot.
 * <ul>
 *   <li>{@code online} reflects {@link com.ronkadosh.bubbleup.common.websocket.WebSocketUserTracker}
 *       — true iff the user has at least one live STOMP session right now.</li>
 *   <li>{@code lastSeenAt} is the last persisted timestamp; null only if the user
 *       has never connected over WebSocket yet.</li>
 * </ul>
 */
public record PresenceResponse(UUID userId, boolean online, Instant lastSeenAt) {
}
