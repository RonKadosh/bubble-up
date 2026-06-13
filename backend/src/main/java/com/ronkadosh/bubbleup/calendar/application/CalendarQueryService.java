package com.ronkadosh.bubbleup.calendar.application;

import com.ronkadosh.bubbleup.calendar.api.dto.CalendarEventResponse;
import com.ronkadosh.bubbleup.calendar.model.CalendarEvent;
import com.ronkadosh.bubbleup.calendar.model.CalendarEventType;
import com.ronkadosh.bubbleup.calendar.model.CalendarOwnerType;
import com.ronkadosh.bubbleup.calendar.persistence.CalendarEventRepository;
import com.ronkadosh.bubbleup.common.error.AppException;
import com.ronkadosh.bubbleup.common.error.ErrorCode;
import com.ronkadosh.bubbleup.expert.internal.ExpertInternalService;
import com.ronkadosh.bubbleup.groups.internal.GroupInternalService;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
public class CalendarQueryService {

    private final CalendarEventRepository repo;
    private final GroupInternalService groupInternalService;
    private final ExpertInternalService expertInternalService;

    // @Lazy: expert module reads from CalendarInternalService at boot to resolve
    // event start/end for session summaries; this back-reference would form a
    // construction cycle with the eager wiring otherwise.
    public CalendarQueryService(
            CalendarEventRepository repo,
            GroupInternalService groupInternalService,
            @Lazy ExpertInternalService expertInternalService) {
        this.repo = repo;
        this.groupInternalService = groupInternalService;
        this.expertInternalService = expertInternalService;
    }

    @Transactional(readOnly = true)
    public List<CalendarEventResponse> list(
            CalendarOwnerType ownerType,
            UUID ownerId,
            Instant from,
            Instant to,
            UUID requesterId
    ) {
        requireOwnerAccess(ownerType, ownerId, requesterId);
        Instant fromOrDefault = from != null ? from : defaultMonthStart();
        Instant toOrDefault = to != null ? to : defaultMonthEnd();
        List<CalendarEventResponse> direct = repo.findInRange(ownerType, ownerId, fromOrDefault, toOrDefault).stream()
                .map(CalendarEventResponse::from)
                .toList();
        if (ownerType != CalendarOwnerType.GROUP) {
            return direct;
        }
        // Merge in expert-session events the group is currently enrolled in.
        // These events live on the expert's USER calendar; enrollment is the
        // visibility key so the group sees them on its own calendar without
        // duplicating event rows.
        List<UUID> enrolledIds = expertInternalService.findActiveSessionCalendarEventIdsForGroup(ownerId);
        if (enrolledIds.isEmpty()) {
            return direct;
        }
        List<CalendarEventResponse> enrolled = repo.findAllById(enrolledIds).stream()
                .filter(e -> !e.getStartsAt().isAfter(toOrDefault) && !e.getEndsAt().isBefore(fromOrDefault))
                .map(CalendarEventResponse::from)
                .toList();
        if (enrolled.isEmpty()) {
            return direct;
        }
        List<CalendarEventResponse> merged = new ArrayList<>(direct.size() + enrolled.size());
        merged.addAll(direct);
        merged.addAll(enrolled);
        return merged;
    }

    @Transactional(readOnly = true)
    public CalendarEventResponse get(UUID eventId, UUID requesterId) {
        CalendarEvent event = repo.findById(eventId)
                .orElseThrow(() -> new AppException(ErrorCode.CALENDAR_EVENT_NOT_FOUND));
        if (event.getEventType() == CalendarEventType.EXPERT_SESSION
                && expertInternalService.isAuthorizedForSessionCalendarEvent(eventId, requesterId)) {
            return CalendarEventResponse.from(event);
        }
        requireOwnerAccess(event.getOwnerType(), event.getOwnerId(), requesterId);
        return CalendarEventResponse.from(event);
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

    private static Instant defaultMonthStart() {
        return YearMonth.now(ZoneOffset.UTC).atDay(1).atStartOfDay(ZoneOffset.UTC).toInstant();
    }

    private static Instant defaultMonthEnd() {
        return YearMonth.now(ZoneOffset.UTC).plusMonths(1).atDay(1).atStartOfDay(ZoneOffset.UTC).toInstant();
    }
}
