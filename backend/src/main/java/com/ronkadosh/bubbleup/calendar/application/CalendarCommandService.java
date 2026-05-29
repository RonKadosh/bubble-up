package com.ronkadosh.bubbleup.calendar.application;

import com.ronkadosh.bubbleup.calendar.api.dto.CalendarEventResponse;
import com.ronkadosh.bubbleup.calendar.api.dto.CreateCalendarEventRequest;
import com.ronkadosh.bubbleup.calendar.api.dto.UpdateCalendarEventRequest;
import com.ronkadosh.bubbleup.calendar.model.CalendarEvent;
import com.ronkadosh.bubbleup.calendar.model.CalendarEventType;
import com.ronkadosh.bubbleup.calendar.model.CalendarOwnerType;
import com.ronkadosh.bubbleup.calendar.persistence.CalendarEventRepository;
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

    @Transactional
    public CalendarEventResponse create(CreateCalendarEventRequest request, UUID userId) {
        requireOwnerAccess(request.ownerType(), request.ownerId(), userId);
        requireValidRange(request.startsAt(), request.endsAt());
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
        boolean hadRoom = shouldHaveRoom(event);
        if (request.eventType() != null) event.setEventType(request.eventType());
        if (request.description() != null) event.setDescription(request.description());
        if (request.startsAt() != null) event.setStartsAt(request.startsAt());
        if (request.endsAt() != null) event.setEndsAt(request.endsAt());
        requireValidRange(event.getStartsAt(), event.getEndsAt());
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
     * "One active session per group at a time": when scheduling a STUDY_SESSION
     * on a GROUP's calendar, reject if the group is already enrolled in an
     * expert session whose time window overlaps. We don't gate other event
     * types — only the room-bound STUDY_SESSION can conflict with an expert
     * session room.
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
        if (!expertInternalService.findActiveSessionsOverlappingForGroup(ownerId, startsAt, endsAt).isEmpty()) {
            throw new AppException(ErrorCode.GROUP_SCHEDULE_CONFLICT);
        }
    }
}
