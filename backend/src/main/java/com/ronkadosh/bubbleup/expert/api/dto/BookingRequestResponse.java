package com.ronkadosh.bubbleup.expert.api.dto;

import com.ronkadosh.bubbleup.expert.model.BookingRequestStatus;
import com.ronkadosh.bubbleup.expert.model.ExpertSessionBookingRequest;

import java.time.Instant;
import java.util.UUID;

public record BookingRequestResponse(
        UUID id,
        UUID expertUserId,
        UUID groupId,
        UUID requestedBy,
        Instant proposedStartsAt,
        Instant proposedEndsAt,
        String message,
        BookingRequestStatus status,
        UUID acceptedSessionId,
        Instant decidedAt,
        Instant createdAt
) {
    public static BookingRequestResponse from(ExpertSessionBookingRequest r) {
        return new BookingRequestResponse(
                r.getId(),
                r.getExpertUserId(),
                r.getGroupId(),
                r.getRequestedBy(),
                r.getProposedStartsAt(),
                r.getProposedEndsAt(),
                r.getMessage(),
                r.getStatus(),
                r.getAcceptedSessionId(),
                r.getDecidedAt(),
                r.getCreatedAt()
        );
    }
}
