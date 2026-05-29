package com.ronkadosh.bubbleup.calendar.internal.dto;

import com.ronkadosh.bubbleup.calendar.model.CalendarEventType;
import com.ronkadosh.bubbleup.calendar.model.CalendarOwnerType;

import java.time.Instant;
import java.util.UUID;

public record CalendarEventSummary(
        UUID id,
        CalendarOwnerType ownerType,
        UUID ownerId,
        CalendarEventType eventType,
        String description,
        Instant startsAt,
        Instant endsAt,
        UUID createdBy
) {}
