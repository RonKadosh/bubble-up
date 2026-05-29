package com.ronkadosh.bubbleup.expert.internal.dto;

import com.ronkadosh.bubbleup.expert.model.ExpertSessionStatus;

import java.time.Instant;
import java.util.UUID;

/**
 * Cross-module slice of an expert session. {@code startsAt} / {@code endsAt}
 * come from the linked calendar event — the entity itself doesn't store them.
 */
public record ExpertSessionSummary(
        UUID id,
        UUID expertUserId,
        UUID calendarEventId,
        String title,
        int capacity,
        int enrolledGroupCount,
        ExpertSessionStatus status,
        Instant startsAt,
        Instant endsAt
) {}
