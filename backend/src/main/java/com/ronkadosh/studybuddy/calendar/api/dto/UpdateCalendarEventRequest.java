package com.ronkadosh.studybuddy.calendar.api.dto;

import com.ronkadosh.studybuddy.calendar.model.CalendarEventType;
import jakarta.validation.constraints.Size;

import java.time.Instant;

public record UpdateCalendarEventRequest(
        CalendarEventType eventType,
        @Size(max = 2000) String description,
        Instant startsAt,
        Instant endsAt
) {}
