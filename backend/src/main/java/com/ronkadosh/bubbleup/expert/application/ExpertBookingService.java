package com.ronkadosh.bubbleup.expert.application;

import com.ronkadosh.bubbleup.common.datetime.TimeProvider;
import com.ronkadosh.bubbleup.common.error.AppException;
import com.ronkadosh.bubbleup.common.error.ErrorCode;
import com.ronkadosh.bubbleup.expert.api.dto.BookingRequestResponse;
import com.ronkadosh.bubbleup.expert.api.dto.CreateBookingRequest;
import com.ronkadosh.bubbleup.expert.api.dto.CreateExpertSessionRequest;
import com.ronkadosh.bubbleup.expert.api.dto.ExpertSessionResponse;
import com.ronkadosh.bubbleup.expert.model.BookingRequestStatus;
import com.ronkadosh.bubbleup.expert.model.ExpertSessionBookingRequest;
import com.ronkadosh.bubbleup.expert.model.ExpertSessionGroupEnrollment;
import com.ronkadosh.bubbleup.expert.model.ExpertSessionStatus;
import com.ronkadosh.bubbleup.expert.model.VerificationStatus;
import com.ronkadosh.bubbleup.expert.persistence.ExpertProfileRepository;
import com.ronkadosh.bubbleup.expert.persistence.ExpertSessionBookingRequestRepository;
import com.ronkadosh.bubbleup.expert.persistence.ExpertSessionGroupEnrollmentRepository;
import com.ronkadosh.bubbleup.expert.persistence.ExpertSessionRepository;
import com.ronkadosh.bubbleup.groups.internal.GroupInternalService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ExpertBookingService {

    private final ExpertSessionBookingRequestRepository requestRepo;
    private final ExpertProfileRepository expertProfileRepo;
    private final ExpertSessionRepository sessionRepo;
    private final ExpertSessionGroupEnrollmentRepository enrollmentRepo;
    private final GroupInternalService groupInternalService;
    private final ExpertSessionCommandService sessionCommands;
    private final TimeProvider timeProvider;

    @Transactional
    public BookingRequestResponse createRequest(UUID requesterId, CreateBookingRequest request) {
        if (!expertProfileRepo.findByUserId(request.expertUserId())
                .map(p -> p.getVerificationStatus() == VerificationStatus.VERIFIED)
                .orElse(false)) {
            throw new AppException(ErrorCode.EXPERT_NOT_VERIFIED);
        }
        if (!groupInternalService.groupExists(request.groupId())) {
            throw new AppException(ErrorCode.GROUP_NOT_FOUND);
        }
        if (!groupInternalService.isOwner(request.groupId(), requesterId)) {
            throw new AppException(ErrorCode.NOT_GROUP_OWNER);
        }
        if (!request.proposedEndsAt().isAfter(request.proposedStartsAt())) {
            throw new AppException(ErrorCode.INVALID_EVENT_TIME_RANGE);
        }
        ExpertSessionBookingRequest saved = requestRepo.save(ExpertSessionBookingRequest.builder()
                .expertUserId(request.expertUserId())
                .groupId(request.groupId())
                .requestedBy(requesterId)
                .proposedStartsAt(request.proposedStartsAt())
                .proposedEndsAt(request.proposedEndsAt())
                .message(request.message())
                .status(BookingRequestStatus.PENDING)
                .build());
        return BookingRequestResponse.from(saved);
    }

    @Transactional
    public BookingRequestResponse acceptRequest(UUID requestId, UUID expertUserId) {
        ExpertSessionBookingRequest req = requireExpertOwned(requestId, expertUserId);
        requirePending(req);

        // Materialize the session via the same flow as a regular create. Capacity
        // is 1 because a booking is implicitly private to the requesting group.
        ExpertSessionResponse session = sessionCommands.createSession(
                expertUserId,
                new CreateExpertSessionRequest(
                        bookingTitle(req),
                        req.getMessage(),
                        req.getProposedStartsAt(),
                        req.getProposedEndsAt(),
                        1
                ));

        // Auto-enroll the requesting group. We bypass the public enrollGroup
        // (which gates on requester=group-owner) because the expert is the actor
        // here, not the group owner. Same-package repo access is the path.
        enrollmentRepo.save(ExpertSessionGroupEnrollment.builder()
                .expertSessionId(session.id())
                .groupId(req.getGroupId())
                .enrolledBy(req.getRequestedBy())
                .build());

        // Flip session to FULL — capacity 1, enrolled 1.
        sessionRepo.findById(session.id()).ifPresent(s -> {
            s.setStatus(ExpertSessionStatus.FULL);
            sessionRepo.save(s);
        });

        req.setStatus(BookingRequestStatus.ACCEPTED);
        req.setAcceptedSessionId(session.id());
        req.setDecidedAt(timeProvider.now());
        requestRepo.save(req);
        return BookingRequestResponse.from(req);
    }

    @Transactional
    public BookingRequestResponse rejectRequest(UUID requestId, UUID expertUserId) {
        ExpertSessionBookingRequest req = requireExpertOwned(requestId, expertUserId);
        requirePending(req);
        req.setStatus(BookingRequestStatus.REJECTED);
        req.setDecidedAt(timeProvider.now());
        return BookingRequestResponse.from(requestRepo.save(req));
    }

    @Transactional
    public BookingRequestResponse withdrawRequest(UUID requestId, UUID requesterId) {
        ExpertSessionBookingRequest req = requestRepo.findById(requestId)
                .orElseThrow(() -> new AppException(ErrorCode.BOOKING_REQUEST_NOT_FOUND));
        if (!req.getRequestedBy().equals(requesterId)) {
            throw new AppException(ErrorCode.NOT_BOOKING_REQUEST_REQUESTER);
        }
        requirePending(req);
        req.setStatus(BookingRequestStatus.WITHDRAWN);
        req.setDecidedAt(timeProvider.now());
        return BookingRequestResponse.from(requestRepo.save(req));
    }

    @Transactional(readOnly = true)
    public List<BookingRequestResponse> listForExpert(UUID expertUserId) {
        return requestRepo.findByExpertUserIdOrderByCreatedAtDesc(expertUserId).stream()
                .map(BookingRequestResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<BookingRequestResponse> listForRequester(UUID requesterId) {
        return requestRepo.findByRequestedByOrderByCreatedAtDesc(requesterId).stream()
                .map(BookingRequestResponse::from)
                .toList();
    }

    private ExpertSessionBookingRequest requireExpertOwned(UUID requestId, UUID expertUserId) {
        ExpertSessionBookingRequest req = requestRepo.findById(requestId)
                .orElseThrow(() -> new AppException(ErrorCode.BOOKING_REQUEST_NOT_FOUND));
        if (!req.getExpertUserId().equals(expertUserId)) {
            throw new AppException(ErrorCode.NOT_BOOKING_REQUEST_EXPERT);
        }
        return req;
    }

    private void requirePending(ExpertSessionBookingRequest req) {
        if (req.getStatus() != BookingRequestStatus.PENDING) {
            throw new AppException(ErrorCode.BOOKING_REQUEST_ALREADY_DECIDED);
        }
    }

    private String bookingTitle(ExpertSessionBookingRequest req) {
        // Group name would be nicer but requires a cross-module call; the
        // accepted session response carries enough metadata (group enrollment)
        // for the frontend to render a friendlier label.
        return "Booked session";
    }
}
