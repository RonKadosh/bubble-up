package com.ronkadosh.bubbleup.expert.application;

import com.ronkadosh.bubbleup.calendar.internal.CalendarInternalService;
import com.ronkadosh.bubbleup.calendar.internal.dto.CalendarEventSummary;
import com.ronkadosh.bubbleup.calendar.model.CalendarEventType;
import com.ronkadosh.bubbleup.calendar.model.CalendarOwnerType;
import com.ronkadosh.bubbleup.common.datetime.TimeProvider;
import com.ronkadosh.bubbleup.common.error.AppException;
import com.ronkadosh.bubbleup.common.error.ErrorCode;
import com.ronkadosh.bubbleup.common.websocket.WebSocketDestination;
import com.ronkadosh.bubbleup.common.websocket.WebSocketPublisher;
import com.ronkadosh.bubbleup.expert.api.dto.CreateExpertSessionRequest;
import com.ronkadosh.bubbleup.expert.api.dto.ExpertSessionResponse;
import com.ronkadosh.bubbleup.expert.model.ExpertProfile;
import com.ronkadosh.bubbleup.expert.model.ExpertSession;
import com.ronkadosh.bubbleup.expert.model.ExpertSessionGroupEnrollment;
import com.ronkadosh.bubbleup.expert.model.ExpertSessionStatus;
import com.ronkadosh.bubbleup.expert.model.VerificationStatus;
import com.ronkadosh.bubbleup.expert.persistence.ExpertProfileRepository;
import com.ronkadosh.bubbleup.expert.persistence.ExpertSessionGroupEnrollmentRepository;
import com.ronkadosh.bubbleup.expert.persistence.ExpertSessionRepository;
import com.ronkadosh.bubbleup.groups.internal.GroupInternalService;
import com.ronkadosh.bubbleup.room.internal.RoomInternalService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ExpertSessionCommandService {

    private final ExpertProfileRepository profileRepo;
    private final ExpertSessionRepository sessionRepo;
    private final ExpertSessionGroupEnrollmentRepository enrollmentRepo;
    private final GroupInternalService groupInternalService;
    private final CalendarInternalService calendarInternalService;
    private final RoomInternalService roomInternalService;
    private final WhiteboardWriterRegistry whiteboardWriterRegistry;
    private final WebSocketPublisher webSocketPublisher;
    private final TimeProvider timeProvider;

    /** Group enrollment closes this long before the session's {@code startsAt}. */
    private static final Duration ENROLLMENT_CLOSES_BEFORE = Duration.ofMinutes(5);

    @Transactional
    public ExpertSessionResponse createSession(UUID expertUserId, CreateExpertSessionRequest request) {
        ExpertProfile profile = profileRepo.findByUserId(expertUserId)
                .orElseThrow(() -> new AppException(ErrorCode.EXPERT_PROFILE_NOT_FOUND));
        if (profile.getVerificationStatus() != VerificationStatus.VERIFIED) {
            throw new AppException(ErrorCode.EXPERT_NOT_VERIFIED);
        }
        CalendarEventSummary event = calendarInternalService.createEventNoAuth(
                CalendarOwnerType.USER,
                expertUserId,
                expertUserId,
                CalendarEventType.EXPERT_SESSION,
                request.title() + (request.description() == null ? "" : " — " + request.description()),
                request.startsAt(),
                request.endsAt()
        );
        ExpertSession session = sessionRepo.save(ExpertSession.builder()
                .expertProfileId(profile.getId())
                .expertUserId(expertUserId)
                .calendarEventId(event.id())
                .title(request.title())
                .description(request.description())
                .capacity(request.capacity())
                .status(ExpertSessionStatus.OPEN)
                .build());
        UUID roomId = roomInternalService.createRoomForExpertSession(
                session.getId(), event.id(), expertUserId);
        return ExpertSessionResponse.from(session, roomId, 0, event.startsAt(), event.endsAt());
    }

    @Transactional
    public void cancelSession(UUID sessionId, UUID requesterId) {
        ExpertSession session = sessionRepo.findById(sessionId)
                .orElseThrow(() -> new AppException(ErrorCode.EXPERT_SESSION_NOT_FOUND));
        if (!session.getExpertUserId().equals(requesterId)) {
            throw new AppException(ErrorCode.EXPERT_SESSION_NOT_HOST);
        }
        session.setStatus(ExpertSessionStatus.CANCELLED);
        sessionRepo.save(session);
        // Tear down the room + chat + whiteboard state. Calendar event remains
        // (cancelled events still appear on the host's calendar marked CANCELLED).
        roomInternalService.deleteRoomForExpertSession(sessionId);
        whiteboardWriterRegistry.clear(sessionId);
        enrollmentRepo.deleteByExpertSessionId(sessionId);
    }

    @Transactional
    public void enrollGroup(UUID sessionId, UUID groupId, UUID requesterId) {
        ExpertSession session = sessionRepo.findById(sessionId)
                .orElseThrow(() -> new AppException(ErrorCode.EXPERT_SESSION_NOT_FOUND));
        if (session.getStatus() == ExpertSessionStatus.CANCELLED
                || session.getStatus() == ExpertSessionStatus.ENDED) {
            throw new AppException(ErrorCode.EXPERT_SESSION_CANCELLED);
        }
        if (!groupInternalService.groupExists(groupId)) {
            throw new AppException(ErrorCode.GROUP_NOT_FOUND);
        }
        if (!groupInternalService.isOwner(groupId, requesterId)) {
            throw new AppException(ErrorCode.NOT_GROUP_OWNER);
        }
        if (enrollmentRepo.existsByExpertSessionIdAndGroupId(sessionId, groupId)) {
            throw new AppException(ErrorCode.EXPERT_SESSION_GROUP_ALREADY_ENROLLED);
        }
        long currentEnrolled = enrollmentRepo.countByExpertSessionId(sessionId);
        if (currentEnrolled >= session.getCapacity()) {
            throw new AppException(ErrorCode.EXPERT_SESSION_CAPACITY_REACHED);
        }
        CalendarEventSummary event = calendarInternalService.findById(session.getCalendarEventId())
                .orElseThrow(() -> new AppException(ErrorCode.CALENDAR_EVENT_NOT_FOUND));
        // Registration window closes 5 minutes before startsAt — at that moment
        // RoomLifecycleScheduler posts the "session is opening" notification to
        // each enrolled group's chat. Late enrollers would miss that signal.
        Instant registrationClosesAt = event.startsAt().minus(ENROLLMENT_CLOSES_BEFORE);
        if (!timeProvider.now().isBefore(registrationClosesAt)) {
            throw new AppException(ErrorCode.EXPERT_SESSION_ENROLLMENT_CLOSED);
        }
        // "One active session per group at a time": reject if the group has
        // a STUDY_SESSION on its calendar overlapping the expert-session window.
        if (calendarInternalService.hasOverlappingStudySession(
                CalendarOwnerType.GROUP, groupId, event.startsAt(), event.endsAt())) {
            throw new AppException(ErrorCode.GROUP_SCHEDULE_CONFLICT);
        }
        enrollmentRepo.save(ExpertSessionGroupEnrollment.builder()
                .expertSessionId(sessionId)
                .groupId(groupId)
                .enrolledBy(requesterId)
                .build());
        if (currentEnrolled + 1 >= session.getCapacity()) {
            session.setStatus(ExpertSessionStatus.FULL);
            sessionRepo.save(session);
        }
    }

    @Transactional
    public void unenrollGroup(UUID sessionId, UUID groupId, UUID requesterId) {
        ExpertSession session = sessionRepo.findById(sessionId)
                .orElseThrow(() -> new AppException(ErrorCode.EXPERT_SESSION_NOT_FOUND));
        if (!groupInternalService.isOwner(groupId, requesterId)) {
            throw new AppException(ErrorCode.NOT_GROUP_OWNER);
        }
        if (!enrollmentRepo.existsByExpertSessionIdAndGroupId(sessionId, groupId)) {
            throw new AppException(ErrorCode.EXPERT_SESSION_GROUP_NOT_ENROLLED);
        }
        enrollmentRepo.deleteByExpertSessionIdAndGroupId(sessionId, groupId);
        if (session.getStatus() == ExpertSessionStatus.FULL) {
            session.setStatus(ExpertSessionStatus.OPEN);
            sessionRepo.save(session);
        }
    }

    @Transactional
    public void grantWhiteboardWrite(UUID sessionId, UUID userId, UUID requesterId) {
        ExpertSession session = sessionRepo.findById(sessionId)
                .orElseThrow(() -> new AppException(ErrorCode.EXPERT_SESSION_NOT_FOUND));
        if (!session.getExpertUserId().equals(requesterId)) {
            throw new AppException(ErrorCode.EXPERT_SESSION_NOT_HOST);
        }
        whiteboardWriterRegistry.grant(sessionId, userId);
        broadcastWriters(sessionId);
    }

    @Transactional
    public void revokeWhiteboardWrite(UUID sessionId, UUID userId, UUID requesterId) {
        ExpertSession session = sessionRepo.findById(sessionId)
                .orElseThrow(() -> new AppException(ErrorCode.EXPERT_SESSION_NOT_FOUND));
        if (!session.getExpertUserId().equals(requesterId)) {
            throw new AppException(ErrorCode.EXPERT_SESSION_NOT_HOST);
        }
        whiteboardWriterRegistry.revoke(sessionId, userId);
        broadcastWriters(sessionId);
    }

    private void broadcastWriters(UUID sessionId) {
        webSocketPublisher.publishToTopic(
                WebSocketDestination.expertSessionWriters(sessionId),
                new com.ronkadosh.bubbleup.expert.api.dto.WhiteboardWritersEvent(
                        sessionId, whiteboardWriterRegistry.writersFor(sessionId))
        );
    }
}
