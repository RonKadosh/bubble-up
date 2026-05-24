package com.ronkadosh.studybuddy.calendar.api.dto;

import com.ronkadosh.studybuddy.calendar.model.CalendarEventType;
import com.ronkadosh.studybuddy.calendar.model.CalendarOwnerType;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.UUID;

public record CreateCalendarEventRequest(
        @NotNull CalendarOwnerType ownerType,
        @NotNull UUID ownerId,
        @NotNull CalendarEventType eventType,
        @Size(max = 2000) String description,
        @NotNull Instant startsAt,
        @NotNull Instant endsAt
) {}
