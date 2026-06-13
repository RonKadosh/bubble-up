package com.ronkadosh.bubbleup.expert.application;

import com.ronkadosh.bubbleup.auth.internal.AuthInternalService;
import com.ronkadosh.bubbleup.auth.internal.dto.UserIdentity;
import com.ronkadosh.bubbleup.calendar.internal.CalendarInternalService;
import com.ronkadosh.bubbleup.calendar.internal.dto.CalendarEventSummary;
import com.ronkadosh.bubbleup.common.error.AppException;
import com.ronkadosh.bubbleup.common.error.ErrorCode;
import com.ronkadosh.bubbleup.expert.api.dto.ExpertSessionParticipantResponse;
import com.ronkadosh.bubbleup.expert.api.dto.ExpertSessionResponse;
import com.ronkadosh.bubbleup.expert.model.ExpertSession;
import com.ronkadosh.bubbleup.expert.model.ExpertSessionStatus;
import com.ronkadosh.bubbleup.expert.persistence.ExpertSessionGroupEnrollmentRepository;
import com.ronkadosh.bubbleup.expert.persistence.ExpertSessionRepository;
import com.ronkadosh.bubbleup.groups.internal.GroupInternalService;
import com.ronkadosh.bubbleup.room.internal.RoomInternalService;
import com.ronkadosh.bubbleup.room.internal.dto.RoomSummary;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ExpertSessionQueryService {

    private final ExpertSessionRepository sessionRepo;
    private final ExpertSessionGroupEnrollmentRepository enrollmentRepo;
    private final CalendarInternalService calendarInternalService;
    private final RoomInternalService roomInternalService;
    private final GroupInternalService groupInternalService;
    private final AuthInternalService authInternalService;
    private final WhiteboardWriterRegistry whiteboardWriterRegistry;

    @Transactional(readOnly = true)
    public ExpertSessionResponse getSession(UUID sessionId) {
        ExpertSession session = sessionRepo.findById(sessionId)
                .orElseThrow(() -> new AppException(ErrorCode.EXPERT_SESSION_NOT_FOUND));
        return toResponse(session);
    }

    @Transactional(readOnly = true)
    public List<ExpertSessionResponse> listForExpert(UUID expertUserId) {
        return sessionRepo.findByExpertUserIdOrderByCreatedAtDesc(expertUserId).stream()
                .map(this::toResponse)
                .toList();
    }

    /**
     * Public browse surface for one expert's currently joinable offerings. Only
     * OPEN sessions are returned; cancelled, ended, live, and full sessions stay
     * off the public profile.
     */
    @Transactional(readOnly = true)
    public List<ExpertSessionResponse> listOpenForExpert(UUID expertUserId) {
        return sessionRepo.findByExpertUserIdAndStatusOrderByCreatedAtDesc(
                        expertUserId, ExpertSessionStatus.OPEN).stream()
                .map(this::toResponse)
                .sorted(this::compareByStartsAt)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<ExpertSessionResponse> listEnrolledForGroup(UUID groupId) {
        return enrollmentRepo.findByGroupId(groupId).stream()
                .map(e -> sessionRepo.findById(e.getExpertSessionId()).orElse(null))
                .filter(java.util.Objects::nonNull)
                .map(this::toResponse)
                .toList();
    }

    /**
     * All sessions in status {@code OPEN} across every expert, sorted by
     * scheduled start time ascending. Used by the directory page so group
     * owners can browse and enroll. v1 returns the lot; pagination /
     * expertise-tag filtering are deferred.
     */
    @Transactional(readOnly = true)
    public List<ExpertSessionResponse> listOpen() {
        return sessionRepo.findByStatusOrderByCreatedAtDesc(ExpertSessionStatus.OPEN).stream()
                .map(this::toResponse)
                .sorted(this::compareByStartsAt)
                .toList();
    }

    /**
     * Everyone authorized to be in the session room: the host plus the union of
     * members across all enrolled groups. Used by the host UI to render a
     * pick-list (grant/revoke whiteboard write) instead of asking for raw UUIDs.
     * The host always appears first; the remainder is sorted by display name.
     */
    @Transactional(readOnly = true)
    public List<ExpertSessionParticipantResponse> listParticipants(UUID sessionId) {
        ExpertSession session = sessionRepo.findById(sessionId)
                .orElseThrow(() -> new AppException(ErrorCode.EXPERT_SESSION_NOT_FOUND));
        UUID hostId = session.getExpertUserId();
        Set<UUID> userIds = new LinkedHashSet<>();
        userIds.add(hostId);
        for (var enrollment : enrollmentRepo.findByExpertSessionId(sessionId)) {
            userIds.addAll(groupInternalService.getMemberUserIds(enrollment.getGroupId()));
        }
        Map<UUID, UserIdentity> identities = authInternalService.getIdentitiesByIds(userIds);
        Set<UUID> writers = whiteboardWriterRegistry.writersFor(sessionId);
        List<ExpertSessionParticipantResponse> rows = new ArrayList<>(userIds.size());
        for (UUID userId : userIds) {
            UserIdentity identity = identities.get(userId);
            String displayName = identity != null && identity.displayName() != null
                    ? identity.displayName()
                    : userId.toString();
            String avatarUrl = identity != null ? identity.avatarUrl() : null;
            boolean isHost = userId.equals(hostId);
            boolean canDraw = isHost || writers.contains(userId);
            rows.add(new ExpertSessionParticipantResponse(userId, displayName, avatarUrl, isHost, canDraw));
        }
        rows.sort(Comparator
                .comparing(ExpertSessionParticipantResponse::isHost).reversed()
                .thenComparing(ExpertSessionParticipantResponse::displayName, String.CASE_INSENSITIVE_ORDER));
        return rows;
    }

    private ExpertSessionResponse toResponse(ExpertSession session) {
        CalendarEventSummary event = calendarInternalService.findById(session.getCalendarEventId())
                .orElse(null);
        Instant startsAt = event != null ? event.startsAt() : null;
        Instant endsAt = event != null ? event.endsAt() : null;
        long enrolled = enrollmentRepo.countByExpertSessionId(session.getId());
        UUID roomId = roomInternalService.findRoomForExpertSession(session.getId())
                .map(RoomSummary::id)
                .orElse(null);
        return ExpertSessionResponse.from(session, roomId, (int) enrolled, startsAt, endsAt);
    }

    private int compareByStartsAt(ExpertSessionResponse a, ExpertSessionResponse b) {
        if (a.startsAt() == null && b.startsAt() == null) return 0;
        if (a.startsAt() == null) return 1;
        if (b.startsAt() == null) return -1;
        return a.startsAt().compareTo(b.startsAt());
    }
}
