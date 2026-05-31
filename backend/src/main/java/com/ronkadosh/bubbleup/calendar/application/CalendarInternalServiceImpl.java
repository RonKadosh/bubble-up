package com.ronkadosh.bubbleup.calendar.application;

import com.ronkadosh.bubbleup.calendar.internal.CalendarInternalService;
import com.ronkadosh.bubbleup.calendar.internal.dto.CalendarEventSummary;
import com.ronkadosh.bubbleup.calendar.model.CalendarEvent;
import com.ronkadosh.bubbleup.calendar.model.CalendarEventType;
import com.ronkadosh.bubbleup.calendar.model.CalendarOwnerType;
import com.ronkadosh.bubbleup.calendar.persistence.CalendarEventRepository;
import com.ronkadosh.bubbleup.room.internal.RoomInternalService;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import org.springframework.data.domain.PageRequest;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

@Service
public class CalendarInternalServiceImpl implements CalendarInternalService {

    private final CalendarEventRepository repo;
    private final RoomInternalService roomInternalService;

    // @Lazy breaks the cycle: RoomInternalServiceImpl needs CalendarInternalService
    // (to look up an event when materializing a room), and we need RoomInternalService
    // here only at delete time, not at construction.
    public CalendarInternalServiceImpl(
            CalendarEventRepository repo,
            @Lazy RoomInternalService roomInternalService) {
        this.repo = repo;
        this.roomInternalService = roomInternalService;
    }

    @Override
    @Transactional(readOnly = true)
    public List<CalendarEventSummary> getEventsForOwner(
            CalendarOwnerType ownerType,
            UUID ownerId,
            Instant from,
            Instant to
    ) {
        return repo.findInRange(ownerType, ownerId, from, to).stream()
                .map(e -> new CalendarEventSummary(
                        e.getId(),
                        e.getOwnerType(),
                        e.getOwnerId(),
                        e.getEventType(),
                        e.getDescription(),
                        e.getStartsAt(),
                        e.getEndsAt(),
                        e.getCreatedBy()
                ))
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<CalendarEventSummary> findUpcomingForOwners(
            CalendarOwnerType ownerType,
            Collection<UUID> ownerIds,
            Instant from,
            Instant to,
            int limit
    ) {
        if (ownerIds == null || ownerIds.isEmpty()) return List.of();
        return repo.findUpcomingForOwners(ownerType, ownerIds, from, to, PageRequest.of(0, limit)).stream()
                .map(e -> new CalendarEventSummary(
                        e.getId(),
                        e.getOwnerType(),
                        e.getOwnerId(),
                        e.getEventType(),
                        e.getDescription(),
                        e.getStartsAt(),
                        e.getEndsAt(),
                        e.getCreatedBy()
                ))
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public java.util.Optional<CalendarEventSummary> findById(UUID eventId) {
        return repo.findById(eventId).map(e -> new CalendarEventSummary(
                e.getId(),
                e.getOwnerType(),
                e.getOwnerId(),
                e.getEventType(),
                e.getDescription(),
                e.getStartsAt(),
                e.getEndsAt(),
                e.getCreatedBy()
        ));
    }

    @Override
    @Transactional
    public CalendarEventSummary bumpEndsAt(UUID eventId, java.time.Duration delta) {
        CalendarEvent event = repo.findById(eventId)
                .orElseThrow(() -> new com.ronkadosh.bubbleup.common.error.AppException(
                        com.ronkadosh.bubbleup.common.error.ErrorCode.CALENDAR_EVENT_NOT_FOUND));
        event.setEndsAt(event.getEndsAt().plus(delta));
        return new CalendarEventSummary(
                event.getId(),
                event.getOwnerType(),
                event.getOwnerId(),
                event.getEventType(),
                event.getDescription(),
                event.getStartsAt(),
                event.getEndsAt(),
                event.getCreatedBy()
        );
    }

    @Override
    @Transactional
    public CalendarEventSummary createEventNoAuth(
            CalendarOwnerType ownerType,
            UUID ownerId,
            UUID createdBy,
            CalendarEventType eventType,
            String description,
            Instant startsAt,
            Instant endsAt
    ) {
        if (!endsAt.isAfter(startsAt)) {
            throw new com.ronkadosh.bubbleup.common.error.AppException(
                    com.ronkadosh.bubbleup.common.error.ErrorCode.INVALID_EVENT_TIME_RANGE);
        }
        CalendarEvent saved = repo.save(CalendarEvent.builder()
                .ownerType(ownerType)
                .ownerId(ownerId)
                .createdBy(createdBy)
                .eventType(eventType)
                .description(description)
                .startsAt(startsAt)
                .endsAt(endsAt)
                .build());
        return new CalendarEventSummary(
                saved.getId(),
                saved.getOwnerType(),
                saved.getOwnerId(),
                saved.getEventType(),
                saved.getDescription(),
                saved.getStartsAt(),
                saved.getEndsAt(),
                saved.getCreatedBy()
        );
    }

    @Override
    @Transactional
    public void deleteEventNoAuth(UUID eventId) {
        repo.findById(eventId).ifPresent(repo::delete);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean hasOverlappingStudySession(
            CalendarOwnerType ownerType, UUID ownerId, Instant startsAt, Instant endsAt) {
        return !repo.findGroupStudySessionsOverlapping(ownerType, ownerId, startsAt, endsAt).isEmpty();
    }

    @Override
    @Transactional
    public void deleteEventsForOwner(CalendarOwnerType ownerType, UUID ownerId) {
        // Room rows reference events; delete the rooms for STUDY_SESSION events
        // first so the room module's cleanup (whiteboard snapshot, etc.) runs
        // before the underlying event rows go away.
        List<CalendarEvent> events = repo.findAllByOwner(ownerType, ownerId);
        for (CalendarEvent event : events) {
            if (event.getEventType() == CalendarEventType.STUDY_SESSION) {
                roomInternalService.deleteRoomForCalendarEvent(event.getId());
            }
        }
        repo.deleteAllByOwner(ownerType, ownerId);
    }
}
