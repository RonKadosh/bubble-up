package com.ronkadosh.bubbleup.calendar.internal;

import com.ronkadosh.bubbleup.calendar.internal.dto.CalendarEventSummary;
import com.ronkadosh.bubbleup.calendar.model.CalendarOwnerType;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface CalendarInternalService {

    List<CalendarEventSummary> getEventsForOwner(
            CalendarOwnerType ownerType,
            UUID ownerId,
            Instant from,
            Instant to
    );

    /**
     * Upcoming events across many owners (e.g. all of a user's groups), starting within
     * {@code [from, to]}, soonest first, capped at {@code limit}. Drives the dashboard
     * feed's "Upcoming" section.
     */
    List<CalendarEventSummary> findUpcomingForOwners(
            CalendarOwnerType ownerType,
            Collection<UUID> ownerIds,
            Instant from,
            Instant to,
            int limit
    );

    java.util.Optional<CalendarEventSummary> findById(UUID eventId);

    /**
     * Adds {@code delta} to the event's {@code endsAt}. Used by the room extend
     * flow. Caller is responsible for auth/membership — this method does not
     * check anything.
     *
     * @throws com.ronkadosh.bubbleup.common.error.AppException with
     *         {@code CALENDAR_EVENT_NOT_FOUND} if the event doesn't exist.
     */
    CalendarEventSummary bumpEndsAt(UUID eventId, java.time.Duration delta);

    /**
     * Creates an event without any authorization check — caller owns the policy.
     * Used by the expert module to materialize a calendar event for an expert
     * session before the room is wired. The skipping of {@code CalendarCommandService}'s
     * {@code requireOwnerAccess} is intentional: the expert module has already
     * verified the caller is the verified expert who owns the session.
     */
    CalendarEventSummary createEventNoAuth(
            CalendarOwnerType ownerType,
            UUID ownerId,
            UUID createdBy,
            com.ronkadosh.bubbleup.calendar.model.CalendarEventType eventType,
            String description,
            Instant startsAt,
            Instant endsAt
    );

    /**
     * Deletes an event by id without auth check. Caller-owned policy.
     * Used by the expert module when a session is cancelled / removed.
     */
    void deleteEventNoAuth(UUID eventId);

    void deleteEventsForOwner(CalendarOwnerType ownerType, UUID ownerId);

    /**
     * True iff the owner already has a {@code STUDY_SESSION} event overlapping
     * the half-open window {@code [startsAt, endsAt)}. Used by the
     * "one active session per group at a time" rule when enrolling a group in
     * an expert session.
     */
    boolean hasOverlappingStudySession(
            CalendarOwnerType ownerType, UUID ownerId, Instant startsAt, Instant endsAt);
}
