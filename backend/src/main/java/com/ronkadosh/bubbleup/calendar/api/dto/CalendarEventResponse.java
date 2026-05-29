package com.ronkadosh.bubbleup.calendar.api.dto;

import com.ronkadosh.bubbleup.calendar.model.CalendarEvent;
import com.ronkadosh.bubbleup.calendar.model.CalendarEventType;
import com.ronkadosh.bubbleup.calendar.model.CalendarOwnerType;

import java.time.Instant;
import java.util.UUID;

public record CalendarEventResponse(
        UUID id,
        CalendarOwnerType ownerType,
        UUID ownerId,
        UUID createdBy,
        CalendarEventType eventType,
        String description,
        Instant startsAt,
        Instant endsAt,
        Instant createdAt,
        Instant updatedAt
) {
    public static CalendarEventResponse from(CalendarEvent e) {
        return new CalendarEventResponse(
                e.getId(),
                e.getOwnerType(),
                e.getOwnerId(),
                e.getCreatedBy(),
                e.getEventType(),
                e.getDescription(),
                e.getStartsAt(),
                e.getEndsAt(),
                e.getCreatedAt(),
                e.getUpdatedAt()
        );
    }
}
