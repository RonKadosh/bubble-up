package com.ronkadosh.studybuddy.calendar.internal;

import com.ronkadosh.studybuddy.calendar.internal.dto.CalendarEventSummary;
import com.ronkadosh.studybuddy.calendar.model.CalendarOwnerType;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface CalendarInternalService {

    List<CalendarEventSummary> getEventsForOwner(
            CalendarOwnerType ownerType,
            UUID ownerId,
            Instant from,
            Instant to
    );

    void deleteEventsForOwner(CalendarOwnerType ownerType, UUID ownerId);
}
