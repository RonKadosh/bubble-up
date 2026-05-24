package com.ronkadosh.studybuddy.calendar.internal.dto;

import com.ronkadosh.studybuddy.calendar.model.CalendarEventType;

import java.time.Instant;
import java.util.UUID;

public record CalendarEventSummary(
        UUID id,
        CalendarEventType eventType,
        String description,
        Instant startsAt,
        Instant endsAt,
        UUID createdBy
) {}
