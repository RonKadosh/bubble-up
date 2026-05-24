package com.ronkadosh.studybuddy.calendar.application;

import com.ronkadosh.studybuddy.calendar.api.dto.CalendarEventResponse;
import com.ronkadosh.studybuddy.calendar.api.dto.CreateCalendarEventRequest;
import com.ronkadosh.studybuddy.calendar.api.dto.UpdateCalendarEventRequest;
import com.ronkadosh.studybuddy.calendar.model.CalendarEvent;
import com.ronkadosh.studybuddy.calendar.model.CalendarOwnerType;
import com.ronkadosh.studybuddy.calendar.persistence.CalendarEventRepository;
import com.ronkadosh.studybuddy.common.error.AppException;
import com.ronkadosh.studybuddy.common.error.ErrorCode;
import com.ronkadosh.studybuddy.groups.internal.GroupInternalService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CalendarCommandService {

    private final CalendarEventRepository repo;
    private final GroupInternalService groupInternalService;

    @Transactional
    public CalendarEventResponse create(CreateCalendarEventRequest request, UUID userId) {
        requireOwnerAccess(request.ownerType(), request.ownerId(), userId);
        requireValidRange(request.startsAt(), request.endsAt());
        CalendarEvent event = repo.save(CalendarEvent.builder()
                .ownerType(request.ownerType())
                .ownerId(request.ownerId())
                .createdBy(userId)
                .eventType(request.eventType())
                .description(request.description())
                .startsAt(request.startsAt())
                .endsAt(request.endsAt())
                .build());
        return CalendarEventResponse.from(event);
    }

    @Transactional
    public CalendarEventResponse update(UUID eventId, UpdateCalendarEventRequest request, UUID userId) {
        CalendarEvent event = repo.findById(eventId)
                .orElseThrow(() -> new AppException(ErrorCode.CALENDAR_EVENT_NOT_FOUND));
        if (!canMutate(event, userId)) {
            throw new AppException(ErrorCode.NOT_EVENT_AUTHOR_OR_OWNER);
        }
        if (request.eventType() != null) event.setEventType(request.eventType());
        if (request.description() != null) event.setDescription(request.description());
        if (request.startsAt() != null) event.setStartsAt(request.startsAt());
        if (request.endsAt() != null) event.setEndsAt(request.endsAt());
        requireValidRange(event.getStartsAt(), event.getEndsAt());
        return CalendarEventResponse.from(event);
    }

    @Transactional
    public void delete(UUID eventId, UUID userId) {
        CalendarEvent event = repo.findById(eventId)
                .orElseThrow(() -> new AppException(ErrorCode.CALENDAR_EVENT_NOT_FOUND));
        if (!canMutate(event, userId)) {
            throw new AppException(ErrorCode.NOT_EVENT_AUTHOR_OR_OWNER);
        }
        repo.delete(event);
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
}
