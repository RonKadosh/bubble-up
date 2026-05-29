package com.ronkadosh.bubbleup.expert.application;

import com.ronkadosh.bubbleup.calendar.internal.CalendarInternalService;
import com.ronkadosh.bubbleup.calendar.internal.dto.CalendarEventSummary;
import com.ronkadosh.bubbleup.common.datetime.TimeProvider;
import com.ronkadosh.bubbleup.expert.internal.ExpertInternalService;
import com.ronkadosh.bubbleup.expert.internal.dto.ExpertProfileSummary;
import com.ronkadosh.bubbleup.expert.internal.dto.ExpertSessionSummary;
import com.ronkadosh.bubbleup.expert.model.ExpertProfile;
import com.ronkadosh.bubbleup.expert.model.ExpertSession;
import com.ronkadosh.bubbleup.expert.model.ExpertSessionStatus;
import com.ronkadosh.bubbleup.expert.model.VerificationStatus;
import com.ronkadosh.bubbleup.expert.persistence.ExpertProfileRepository;
import com.ronkadosh.bubbleup.expert.persistence.ExpertSessionGroupEnrollmentRepository;
import com.ronkadosh.bubbleup.expert.persistence.ExpertSessionRepository;
import com.ronkadosh.bubbleup.groups.internal.GroupInternalService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ExpertInternalServiceImpl implements ExpertInternalService {

    /** Mirror of {@code RoomQueryService.OPEN_BEFORE} for the EXPERT_SESSION scope. */
    private static final Duration OPEN_BEFORE = Duration.ofMinutes(15);

    private final ExpertProfileRepository profileRepo;
    private final ExpertSessionRepository sessionRepo;
    private final ExpertSessionGroupEnrollmentRepository enrollmentRepo;
    private final GroupInternalService groupInternalService;
    private final CalendarInternalService calendarInternalService;
    private final TimeProvider timeProvider;

    @Override
    @Transactional(readOnly = true)
    public boolean isVerifiedExpert(UUID userId) {
        return profileRepo.findByUserId(userId)
                .map(p -> p.getVerificationStatus() == VerificationStatus.VERIFIED)
                .orElse(false);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<ExpertProfileSummary> findProfileByUser(UUID userId) {
        return profileRepo.findByUserId(userId).map(ExpertInternalServiceImpl::toProfileSummary);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<ExpertSessionSummary> findSessionById(UUID sessionId) {
        return sessionRepo.findById(sessionId).map(this::toSessionSummary);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean isSessionHost(UUID sessionId, UUID userId) {
        return sessionRepo.findById(sessionId)
                .map(s -> s.getExpertUserId().equals(userId))
                .orElse(false);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean isAuthorizedForSession(UUID sessionId, UUID userId) {
        Optional<ExpertSession> session = sessionRepo.findById(sessionId);
        if (session.isEmpty()) return false;
        if (session.get().getExpertUserId().equals(userId)) return true;
        // Any enrolled-group membership grants access.
        return enrollmentRepo.findByExpertSessionId(sessionId).stream()
                .anyMatch(e -> groupInternalService.isMember(e.getGroupId(), userId));
    }

    @Override
    @Transactional(readOnly = true)
    public java.util.List<UUID> findActiveSessionCalendarEventIdsForGroup(UUID groupId) {
        return enrollmentRepo.findByGroupId(groupId).stream()
                .map(e -> sessionRepo.findById(e.getExpertSessionId()).orElse(null))
                .filter(java.util.Objects::nonNull)
                .filter(s -> s.getStatus() != ExpertSessionStatus.CANCELLED
                        && s.getStatus() != ExpertSessionStatus.ENDED)
                .map(ExpertSession::getCalendarEventId)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public java.util.List<UUID> findEnrolledGroupIds(UUID sessionId) {
        return enrollmentRepo.findByExpertSessionId(sessionId).stream()
                .map(e -> e.getGroupId())
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public java.util.List<ExpertSessionSummary> findActiveSessionsOverlappingForGroup(
            UUID groupId, Instant startsAt, Instant endsAt) {
        return enrollmentRepo.findByGroupId(groupId).stream()
                .map(e -> sessionRepo.findById(e.getExpertSessionId()).orElse(null))
                .filter(java.util.Objects::nonNull)
                .filter(s -> s.getStatus() != ExpertSessionStatus.CANCELLED
                        && s.getStatus() != ExpertSessionStatus.ENDED)
                .map(this::toSessionSummary)
                .filter(sum -> sum.startsAt() != null && sum.endsAt() != null)
                .filter(sum -> sum.endsAt().isAfter(startsAt) && sum.startsAt().isBefore(endsAt))
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public boolean canEnterRoomNow(UUID sessionId, UUID userId) {
        Optional<ExpertSession> sessionOpt = sessionRepo.findById(sessionId);
        if (sessionOpt.isEmpty()) return false;
        ExpertSession session = sessionOpt.get();
        if (session.getStatus() == ExpertSessionStatus.CANCELLED
                || session.getStatus() == ExpertSessionStatus.ENDED) {
            return false;
        }
        Optional<CalendarEventSummary> event = calendarInternalService.findById(session.getCalendarEventId());
        if (event.isEmpty()) return false;
        Instant now = timeProvider.now();
        Instant opensAt = event.get().startsAt().minus(OPEN_BEFORE);
        if (now.isBefore(opensAt) || now.isAfter(event.get().endsAt())) {
            return false;
        }
        return isAuthorizedForSession(sessionId, userId);
    }

    private ExpertSessionSummary toSessionSummary(ExpertSession s) {
        Optional<CalendarEventSummary> event = calendarInternalService.findById(s.getCalendarEventId());
        Instant startsAt = event.map(CalendarEventSummary::startsAt).orElse(null);
        Instant endsAt = event.map(CalendarEventSummary::endsAt).orElse(null);
        long enrolled = enrollmentRepo.countByExpertSessionId(s.getId());
        return new ExpertSessionSummary(
                s.getId(),
                s.getExpertUserId(),
                s.getCalendarEventId(),
                s.getTitle(),
                s.getCapacity(),
                (int) enrolled,
                s.getStatus(),
                startsAt,
                endsAt
        );
    }

    private static ExpertProfileSummary toProfileSummary(ExpertProfile p) {
        return new ExpertProfileSummary(
                p.getId(),
                p.getUserId(),
                p.getHeadline(),
                Set.copyOf(p.getExpertiseTags()),
                p.getVerificationStatus()
        );
    }
}
