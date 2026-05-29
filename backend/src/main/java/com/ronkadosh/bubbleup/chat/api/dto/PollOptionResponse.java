package com.ronkadosh.bubbleup.chat.api.dto;

import java.util.List;
import java.util.UUID;

/**
 * One option of a poll, with its current attributed vote list.
 */
public record PollOptionResponse(
        UUID id,
        String text,
        int position,
        int voteCount,
        List<UUID> voters
) {}
