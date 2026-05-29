package com.ronkadosh.bubbleup.expert.api.dto;

import com.ronkadosh.bubbleup.expert.internal.dto.ExpertSessionSummary;
import com.ronkadosh.bubbleup.expert.model.ExpertSession;
import com.ronkadosh.bubbleup.expert.model.ExpertSessionStatus;

import java.time.Instant;
import java.util.UUID;

public record ExpertSessionResponse(
        UUID id,
        UUID expertUserId,
        UUID calendarEventId,
        UUID roomId,
        String title,
        String description,
        int capacity,
        int enrolledGroupCount,
        ExpertSessionStatus status,
        Instant startsAt,
        Instant endsAt,
        Instant createdAt
) {
    public static ExpertSessionResponse from(
            ExpertSession session,
            UUID roomId,
            int enrolledGroupCount,
            Instant startsAt,
            Instant endsAt
    ) {
        return new ExpertSessionResponse(
                session.getId(),
                session.getExpertUserId(),
                session.getCalendarEventId(),
                roomId,
                session.getTitle(),
                session.getDescription(),
                session.getCapacity(),
                enrolledGroupCount,
                session.getStatus(),
                startsAt,
                endsAt,
                session.getCreatedAt()
        );
    }

    public static ExpertSessionResponse fromSummary(ExpertSessionSummary s, UUID roomId, String description, Instant createdAt) {
        return new ExpertSessionResponse(
                s.id(),
                s.expertUserId(),
                s.calendarEventId(),
                roomId,
                s.title(),
                description,
                s.capacity(),
                s.enrolledGroupCount(),
                s.status(),
                s.startsAt(),
                s.endsAt(),
                createdAt
        );
    }
}
