package com.ronkadosh.bubbleup.calendar.application;

import com.ronkadosh.bubbleup.calendar.api.dto.CalendarEventResponse;
import com.ronkadosh.bubbleup.calendar.api.dto.CreateCalendarEventRequest;
import com.ronkadosh.bubbleup.calendar.api.dto.UpdateCalendarEventRequest;
import com.ronkadosh.bubbleup.calendar.model.CalendarEvent;
import com.ronkadosh.bubbleup.calendar.model.CalendarEventType;
import com.ronkadosh.bubbleup.calendar.model.CalendarOwnerType;
import com.ronkadosh.bubbleup.calendar.persistence.CalendarEventRepository;
import com.ronkadosh.bubbleup.common.datetime.TimeProvider;
import com.ronkadosh.bubbleup.common.error.AppException;
import com.ronkadosh.bubbleup.common.error.ErrorCode;
import com.ronkadosh.bubbleup.common.events.BehaviorEventType;
import com.ronkadosh.bubbleup.common.events.UserBehaviorEvent;
import com.ronkadosh.bubbleup.expert.internal.ExpertInternalService;
import com.ronkadosh.bubbleup.groups.internal.GroupInternalService;
import com.ronkadosh.bubbleup.room.internal.RoomInternalService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CalendarCommandService {

    private final CalendarEventRepository repo;
    private final GroupInternalService groupInternalService;
    private final RoomInternalService roomInternalService;
    private final ExpertInternalService expertInternalService;
    private final ApplicationEventPublisher eventPublisher;
    private final TimeProvider timeProvider;

    @Transactional
    public CalendarEventResponse create(CreateCalendarEventRequest request, UUID userId) {
        requireOwnerAccess(request.ownerType(), request.ownerId(), userId);
        requireValidRange(request.startsAt(), request.endsAt());
        requireNotInPast(request.startsAt());
        requireNoGroupConflict(request.ownerType(), request.ownerId(),
                request.eventType(), request.startsAt(), request.endsAt());
        CalendarEvent event = repo.save(CalendarEvent.builder()
                .ownerType(request.ownerType())
                .ownerId(request.ownerId())
                .createdBy(userId)
                .eventType(request.eventType())
                .description(request.description())
                .startsAt(request.startsAt())
                .endsAt(request.endsAt())
                .build());
        if (shouldHaveRoom(event)) {
            roomInternalService.createRoomForCalendarEvent(event.getId(), userId);
        }
        eventPublisher.publishEvent(new UserBehaviorEvent(userId, BehaviorEventType.CREATED_CALENDAR_EVENT));
        return CalendarEventResponse.from(event);
    }

    @Transactional
    public CalendarEventResponse update(UUID eventId, UpdateCalendarEventRequest request, UUID userId) {
        CalendarEvent event = repo.findById(eventId)
                .orElseThrow(() -> new AppException(ErrorCode.CALENDAR_EVENT_NOT_FOUND));
        if (!canMutate(event, userId)) {
            throw new AppException(ErrorCode.NOT_EVENT_AUTHOR_OR_OWNER);
        }
        requireNotStarted(event);
        boolean hadRoom = shouldHaveRoom(event);
        if (request.eventType() != null) event.setEventType(request.eventType());
        if (request.description() != null) event.setDescription(request.description());
        if (request.startsAt() != null) event.setStartsAt(request.startsAt());
        if (request.endsAt() != null) event.setEndsAt(request.endsAt());
        requireValidRange(event.getStartsAt(), event.getEndsAt());
        requireNotInPast(event.getStartsAt());
        boolean shouldHaveRoomNow = shouldHaveRoom(event);
        if (!hadRoom && shouldHaveRoomNow) {
            roomInternalService.createRoomForCalendarEvent(event.getId(), userId);
        } else if (hadRoom && !shouldHaveRoomNow) {
            roomInternalService.deleteRoomForCalendarEvent(event.getId());
        }
        return CalendarEventResponse.from(event);
    }

    @Transactional
    public void delete(UUID eventId, UUID userId) {
        CalendarEvent event = repo.findById(eventId)
                .orElseThrow(() -> new AppException(ErrorCode.CALENDAR_EVENT_NOT_FOUND));
        if (!canMutate(event, userId)) {
            throw new AppException(ErrorCode.NOT_EVENT_AUTHOR_OR_OWNER);
        }
        requireNotStarted(event);
        if (shouldHaveRoom(event)) {
            roomInternalService.deleteRoomForCalendarEvent(eventId);
        }
        repo.delete(event);
    }

    private boolean shouldHaveRoom(CalendarEvent event) {
        return event.getEventType() == CalendarEventType.STUDY_SESSION
                && event.getOwnerType() == CalendarOwnerType.GROUP;
    }

    private void requireOwnerAccess(CalendarOwnerType type, UUID ownerId, UUID userId) {
        switch (type) {
            case GROUP -> {
                if (!groupInternalService.groupExists(ownerId)) {
                    throw new AppException(ErrorCode.GROUP_NOT_FOUND);
                }
                if (!groupInternalService.isMember(ownerId, userId)) {
                    throw new AppException(ErrorCode.NOT_GROUP_MEMBER);
                }
            }
            case USER -> {
                if (!ownerId.equals(userId)) {
                    throw new AppException(ErrorCode.NOT_GROUP_MEMBER);
                }
            }
        }
    }

    private boolean canMutate(CalendarEvent event, UUID userId) {
        if (event.getCreatedBy().equals(userId)) return true;
        return event.getOwnerType() == CalendarOwnerType.GROUP
                && groupInternalService.isOwner(event.getOwnerId(), userId);
    }

    private void requireValidRange(Instant startsAt, Instant endsAt) {
        if (!endsAt.isAfter(startsAt)) {
            throw new AppException(ErrorCode.INVALID_EVENT_TIME_RANGE);
        }
    }

    /**
     * Events live in the future: you can't schedule a Bubble in the past, and you
     * can't drag an existing one's start back behind "now". Paired with
     * {@link #requireNotStarted} on update, this fixates the calendar — once an
     * event's start arrives it becomes a historical fact, no longer mutable.
     */
    private void requireNotInPast(Instant startsAt) {
        if (startsAt.isBefore(timeProvider.now())) {
            throw new AppException(ErrorCode.EVENT_STARTS_IN_PAST);
        }
    }

    /**
     * Once an event's start time has passed, the event is fixated — it can no
     * longer be edited or deleted. Mutating a session that's already begun (or
     * ended) would rewrite history and, for STUDY_SESSIONs, churn an
     * already-open/closed room.
     */
    private void requireNotStarted(CalendarEvent event) {
        if (!event.getStartsAt().isAfter(timeProvider.now())) {
            throw new AppException(ErrorCode.EVENT_ALREADY_STARTED);
        }
    }

    /**
     * "One active session per group at a time": when scheduling a STUDY_SESSION
     * on a GROUP's calendar (i.e. going live with a Bubble), reject if the group
     * already has an overlapping session — either:
     *   - another STUDY_SESSION (a second Live Bubble), or
     *   - an enrolled expert session.
     * Both would spawn a second room in the same window, which surfaces as a
     * "ghost" Live Bubble underneath the first. We don't gate other event types —
     * only the room-bound STUDY_SESSION can conflict.
     */
    private void requireNoGroupConflict(
            CalendarOwnerType ownerType,
            UUID ownerId,
            CalendarEventType eventType,
            Instant startsAt,
            Instant endsAt) {
        if (ownerType != CalendarOwnerType.GROUP || eventType != CalendarEventType.STUDY_SESSION) {
            return;
        }
        // Another Live Bubble already scheduled in an overlapping window.
        if (!repo.findGroupStudySessionsOverlapping(
                CalendarOwnerType.GROUP, ownerId, startsAt, endsAt).isEmpty()) {
            throw new AppException(ErrorCode.GROUP_SCHEDULE_CONFLICT);
        }
        // An enrolled expert session in an overlapping window.
        if (!expertInternalService.findActiveSessionsOverlappingForGroup(ownerId, startsAt, endsAt).isEmpty()) {
            throw new AppException(ErrorCode.GROUP_SCHEDULE_CONFLICT);
        }
    }
}
