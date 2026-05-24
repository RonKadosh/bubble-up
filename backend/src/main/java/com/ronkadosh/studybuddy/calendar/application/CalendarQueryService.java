package com.ronkadosh.studybuddy.calendar.application;

import com.ronkadosh.studybuddy.calendar.api.dto.CalendarEventResponse;
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
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CalendarQueryService {

    private final CalendarEventRepository repo;
    private final GroupInternalService groupInternalService;

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
        return repo.findInRange(ownerType, ownerId, fromOrDefault, toOrDefault).stream()
                .map(CalendarEventResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public CalendarEventResponse get(UUID eventId, UUID requesterId) {
        CalendarEvent event = repo.findById(eventId)
                .orElseThrow(() -> new AppException(ErrorCode.CALENDAR_EVENT_NOT_FOUND));
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
