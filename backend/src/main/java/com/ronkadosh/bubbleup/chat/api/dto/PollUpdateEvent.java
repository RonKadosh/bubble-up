package com.ronkadosh.bubbleup.chat.api.dto;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Live update broadcast on {@code /topic/chat/{roomId}/polls} when a vote is cast,
 * retracted, or the poll is closed. Carries attributed voter lists so the UI can
 * refresh both counts and avatar strips without a fetch.
 */
public record PollUpdateEvent(
        UUID pollId,
        Map<UUID, Integer> optionCounts,
        Map<UUID, List<UUID>> optionVoters,
        int totalVotes,
        Instant closedAt
) {}
