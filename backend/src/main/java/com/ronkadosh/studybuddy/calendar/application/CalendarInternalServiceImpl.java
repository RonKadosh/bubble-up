package com.ronkadosh.studybuddy.calendar.application;

import com.ronkadosh.studybuddy.calendar.internal.CalendarInternalService;
import com.ronkadosh.studybuddy.calendar.internal.dto.CalendarEventSummary;
import com.ronkadosh.studybuddy.calendar.model.CalendarOwnerType;
import com.ronkadosh.studybuddy.calendar.persistence.CalendarEventRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CalendarInternalServiceImpl implements CalendarInternalService {

    private final CalendarEventRepository repo;

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
                        e.getEventType(),
                        e.getDescription(),
                        e.getStartsAt(),
                        e.getEndsAt(),
                        e.getCreatedBy()
                ))
                .toList();
    }

    @Override
    @Transactional
    public void deleteEventsForOwner(CalendarOwnerType ownerType, UUID ownerId) {
        repo.deleteAllByOwner(ownerType, ownerId);
    }
}
