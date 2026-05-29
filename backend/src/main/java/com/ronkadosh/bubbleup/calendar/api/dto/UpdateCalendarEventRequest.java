package com.ronkadosh.bubbleup.calendar.api.dto;

import com.ronkadosh.bubbleup.calendar.model.CalendarEventType;
import jakarta.validation.constraints.Size;

import java.time.Instant;

public record UpdateCalendarEventRequest(
        CalendarEventType eventType,
        @Size(max = 2000) String description,
        Instant startsAt,
        Instant endsAt
) {}
