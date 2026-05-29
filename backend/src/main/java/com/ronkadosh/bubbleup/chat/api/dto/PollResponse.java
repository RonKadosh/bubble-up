package com.ronkadosh.bubbleup.chat.api.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Full poll snapshot: options with attributed votes plus the caller's own vote.
 * Returned by {@code POST /chat/rooms/{roomId}/polls}, {@code GET /chat/polls/{id}},
 * {@code POST /chat/polls/{id}/vote}, {@code POST /chat/polls/{id}/close}.
 */
public record PollResponse(
        UUID id,
        UUID roomId,
        UUID messageId,
        String question,
        boolean allowMultiple,
        Instant closedAt,
        UUID createdByUserId,
        Instant createdAt,
        List<PollOptionResponse> options,
        int totalVotes,
        List<UUID> myVote
) {}
