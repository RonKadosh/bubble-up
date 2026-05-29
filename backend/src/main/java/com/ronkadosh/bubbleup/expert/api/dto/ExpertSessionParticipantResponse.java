package com.ronkadosh.bubbleup.expert.api.dto;

import java.util.UUID;

/**
 * Authorized participant in an expert session — the host plus every member of
 * every enrolled group. Returned by
 * {@code GET /expert-sessions/{id}/participants} so the host UI can render a
 * pick-list (grant/revoke whiteboard write) instead of asking for raw user ids.
 *
 * <p>Not a presence snapshot: this includes everyone who <em>may</em> be in the
 * room, not just those currently connected. Live presence is a separate
 * concern (not built in v1).
 */
public record ExpertSessionParticipantResponse(
        UUID userId,
        String displayName,
        String avatarUrl,
        boolean isHost,
        boolean canDrawWhiteboard
) {}
