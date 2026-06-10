package com.ronkadosh.bubbleup.room.application;

import com.ronkadosh.bubbleup.calendar.internal.CalendarInternalService;
import com.ronkadosh.bubbleup.calendar.internal.dto.CalendarEventSummary;
import com.ronkadosh.bubbleup.chat.internal.ChatInternalService;
import com.ronkadosh.bubbleup.chat.model.ChatLinkTargetType;
import com.ronkadosh.bubbleup.chat.model.ChatMessageType;
import com.ronkadosh.bubbleup.common.datetime.TimeProvider;
import com.ronkadosh.bubbleup.common.websocket.WebSocketDestination;
import com.ronkadosh.bubbleup.common.websocket.WebSocketPublisher;
import com.ronkadosh.bubbleup.expert.internal.ExpertInternalService;
import com.ronkadosh.bubbleup.room.model.Room;
import com.ronkadosh.bubbleup.room.model.RoomScope;
import com.ronkadosh.bubbleup.room.persistence.RoomRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Drives the room session lifecycle:
 *
 * <ul>
 *   <li>15 min before {@code endsAt} — posts the SYSTEM_ROOM_END_SOON chat
 *       message with an Extend CTA. Stamps {@code endWarningSentAt} so we
 *       don't double-post. The extend flow clears this stamp, allowing a fresh
 *       warning to fire before the new end time.</li>
 *   <li>At {@code endsAt} — hard-closes the room: sets {@code endedAt}, clears
 *       the in-memory whiteboard snapshot, broadcasts {@code ENDED} on
 *       {@code /topic/rooms/{id}/lifecycle}.</li>
 * </ul>
 *
 * Runs every 30 seconds. Two-tick precision is plenty for a 15-min warning
 * and a hard close — the JaaS JWT exp is also bounded to {@code endsAt} so a
 * client can't sneak past even if we're slow.
 *
 * <p><b>Transaction boundary</b>: {@link #tick()} is intentionally NOT
 * {@code @Transactional}. Each per-room action runs in its own transaction
 * via the self-proxy ({@link #self}). This guarantees that a failure in one
 * row (e.g. a chat-post error) doesn't poison the warning-stamp write for the
 * same room, and never blocks subsequent rooms in the tick. Without this
 * isolation a single failing room would loop forever: the failed insert would
 * mark the surrounding tx rollback-only, discarding the {@code endWarningSentAt}
 * stamp, and the next tick would re-fire the same path.
 *
 * <p>Uses {@code @Lazy} on collaborators that participate in the WS bean graph
 * to mirror the pattern documented in backend/CLAUDE.md for chat.
 */
@Component
@Slf4j
public class RoomLifecycleScheduler {

    /** How early before {@code endsAt} to post the "ends soon" warning. */
    private static final Duration WARN_BEFORE = Duration.ofMinutes(15);
    /** EXPERT_SESSION rooms: how early before {@code startsAt} the
     *  registration window closes and the "session is opening" system message
     *  fires into each enrolled group's chat. */
    private static final Duration REGISTRATION_CLOSES_BEFORE = Duration.ofMinutes(5);

    private final RoomRepository roomRepository;
    private final CalendarInternalService calendarInternalService;
    private final ChatInternalService chatInternalService;
    private final ExpertInternalService expertInternalService;
    private final WhiteboardRelay whiteboardRelay;
    private final WebSocketPublisher webSocketPublisher;
    private final TimeProvider timeProvider;

    /**
     * Self-proxy so {@link #tick()} can invoke the {@code @Transactional}
     * per-room methods through the AOP proxy — direct {@code this.} calls
     * would bypass it.
     */
    private RoomLifecycleScheduler self;

    /**
     * Constructor injection with @Lazy on collaborators that transitively
     * touch WebSocket beans, to break the same cycle as ChatInternalServiceImpl.
     */
    public RoomLifecycleScheduler(
            RoomRepository roomRepository,
            CalendarInternalService calendarInternalService,
            @Lazy ChatInternalService chatInternalService,
            ExpertInternalService expertInternalService,
            WhiteboardRelay whiteboardRelay,
            @Lazy WebSocketPublisher webSocketPublisher,
            TimeProvider timeProvider) {
        this.roomRepository = roomRepository;
        this.calendarInternalService = calendarInternalService;
        this.chatInternalService = chatInternalService;
        this.expertInternalService = expertInternalService;
        this.whiteboardRelay = whiteboardRelay;
        this.webSocketPublisher = webSocketPublisher;
        this.timeProvider = timeProvider;
    }

    @Autowired
    public void setSelf(@Lazy RoomLifecycleScheduler self) {
        this.self = self;
    }

    @Scheduled(fixedDelay = 30_000, initialDelay = 30_000)
    public void tick() {
        Instant now = timeProvider.now();
        List<Room> openRooms;
        try {
            openRooms = roomRepository.findAllByEndedAtIsNull();
        } catch (RuntimeException e) {
            log.warn("[RoomLifecycle] failed to list open rooms; skipping tick: {}", e.getMessage());
            return;
        }
        for (Room room : openRooms) {
            try {
                processRoom(room.getId(), now);
            } catch (RuntimeException e) {
                // Isolate failures per row so one bad room can't stop the rest.
                log.warn("[RoomLifecycle] processing room {} failed: {}", room.getId(), e.getMessage());
            }
        }
    }

    /** Routes a single room through its applicable phase. Not transactional —
     *  the inner self-calls each get their own tx via the proxy. */
    private void processRoom(UUID roomId, Instant now) {
        Optional<Room> roomOpt = roomRepository.findById(roomId);
        if (roomOpt.isEmpty()) return;
        Room room = roomOpt.get();
        if (room.getEndedAt() != null) return;
        if (room.getCalendarEventId() == null) return;

        Optional<CalendarEventSummary> eventOpt =
                calendarInternalService.findById(room.getCalendarEventId());
        if (eventOpt.isEmpty()) return;
        CalendarEventSummary event = eventOpt.get();
        if (event.endsAt() == null) return;

        // Pass A: hard-close if past endsAt.
        if (now.isAfter(event.endsAt())) {
            self.hardCloseTx(roomId, now);
            return;
        }

        // Pass B: 15-min warning. Skip if already sent for this end time.
        if (room.getEndWarningSentAt() == null) {
            Instant warnAt = event.endsAt().minus(WARN_BEFORE);
            if (!now.isBefore(warnAt) && now.isBefore(event.endsAt())) {
                self.sendEndSoonWarningTx(roomId, now);
            }
        }

        // Pass C: EXPERT_SESSION registration close — at startsAt - 5min,
        // post the "session is opening" message to each enrolled group's chat.
        // Idempotent via registrationClosedNotifiedAt.
        if (room.getScope() == RoomScope.EXPERT_SESSION
                && room.getRegistrationClosedNotifiedAt() == null
                && event.startsAt() != null) {
            Instant closesAt = event.startsAt().minus(REGISTRATION_CLOSES_BEFORE);
            if (!now.isBefore(closesAt) && now.isBefore(event.endsAt())) {
                self.notifyRegistrationClosedTx(roomId, now);
            }
        }

        // Pass D: GROUP room opens for joining — at startsAt - GROUP_OPEN_BEFORE,
        // post the "your Bubble is live" card into the group's chat. Idempotent via
        // liveNotifiedAt. Mirrors Pass C, group-scope twin.
        if (room.getScope() == RoomScope.GROUP
                && room.getLiveNotifiedAt() == null
                && event.startsAt() != null) {
            Instant opensAt = event.startsAt().minus(RoomQueryService.GROUP_OPEN_BEFORE);
            if (!now.isBefore(opensAt) && now.isBefore(event.endsAt())) {
                self.notifyGroupRoomLiveTx(roomId, now);
            }
        }
    }

    /**
     * Stamps {@code liveNotifiedAt} on the GROUP room and posts the
     * {@link ChatMessageType#SYSTEM_GROUP_ROOM_OPEN} card into the group's chat.
     * The post runs in a separate transaction so a chat failure can't roll back the
     * stamp (and re-spam every tick) — same pattern as the end-soon warning.
     */
    @Transactional
    public void notifyGroupRoomLiveTx(UUID roomId, Instant now) {
        Room room = roomRepository.findById(roomId).orElse(null);
        if (room == null
                || room.getEndedAt() != null
                || room.getLiveNotifiedAt() != null
                || room.getScope() != RoomScope.GROUP) {
            return;
        }
        room.setLiveNotifiedAt(now);
        roomRepository.save(room);
        UUID groupId = room.getGroupId();
        if (groupId == null) return;
        try {
            self.postGroupRoomLiveMessageTx(groupId, room.getId());
        } catch (RuntimeException e) {
            log.warn("[RoomLifecycle] group-room-live post failed for room {}: {}", room.getId(), e.getMessage());
        }
    }

    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.REQUIRES_NEW)
    public void postGroupRoomLiveMessageTx(UUID groupId, UUID roomId) {
        chatInternalService.postSystemMessageWithLink(
                groupId,
                ChatMessageType.SYSTEM_GROUP_ROOM_OPEN,
                "Your Bubble is live — hop into the room.",
                ChatLinkTargetType.ROOM,
                roomId
        );
    }

    @Transactional
    public void hardCloseTx(UUID roomId, Instant now) {
        Room room = roomRepository.findById(roomId).orElse(null);
        if (room == null || room.getEndedAt() != null) return;
        room.setEndedAt(now);
        roomRepository.save(room);
        whiteboardRelay.clear(room.getId());
        try {
            webSocketPublisher.publishToTopic(
                    WebSocketDestination.roomLifecycle(room.getId()),
                    RoomLifecycleEvent.ended(room.getId())
            );
        } catch (RuntimeException e) {
            // Broadcast is best-effort; failure here must not roll back the endedAt stamp.
            log.warn("[RoomLifecycle] broadcast ENDED failed for room {}: {}", room.getId(), e.getMessage());
        }
        log.info("[RoomLifecycle] hard-closed room {}", room.getId());
    }

    /**
     * Stamps {@code endWarningSentAt} and posts the system chat message in
     * one transaction. The stamp commits even if the chat post fails — we
     * deliberately do NOT swallow the chat exception so the tx commits with
     * the stamp set; if there's a downstream chat layer issue we'd rather
     * skip the warning than log-spam every 30s.
     *
     * <p>The trick: we run the chat post inside its OWN nested call wrapped in
     * a try/catch logger. Because that nested call goes through the self-proxy
     * with {@code REQUIRES_NEW}, its failure doesn't poison our outer tx.
     */
    @Transactional
    public void sendEndSoonWarningTx(UUID roomId, Instant now) {
        Room room = roomRepository.findById(roomId).orElse(null);
        if (room == null || room.getEndWarningSentAt() != null || room.getEndedAt() != null) return;
        room.setEndWarningSentAt(now);
        roomRepository.save(room);
        UUID groupId = room.getGroupId();
        if (groupId == null) return;
        // Post via a separate transaction so a chat failure doesn't roll back our stamp.
        try {
            self.postWarningMessageTx(groupId, room.getId());
        } catch (RuntimeException e) {
            log.warn("[RoomLifecycle] warning post failed for room {}: {}", room.getId(), e.getMessage());
        }
    }

    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.REQUIRES_NEW)
    public void postWarningMessageTx(UUID groupId, UUID roomId) {
        chatInternalService.postSystemMessageWithLink(
                groupId,
                ChatMessageType.SYSTEM_ROOM_END_SOON,
                "Session ends in 15 minutes",
                ChatLinkTargetType.ROOM,
                roomId
        );
    }

    /**
     * Stamps {@code registrationClosedNotifiedAt} on the EXPERT_SESSION room
     * (idempotency for the lifecycle scheduler) and fans out the
     * {@link ChatMessageType#SYSTEM_EXPERT_SESSION_OPEN} link message into each
     * enrolled group's default chat. Per-group post is isolated via a nested
     * REQUIRES_NEW transaction so one group's failure can't poison the others
     * — same defensive pattern as the end-soon warning.
     */
    @Transactional
    public void notifyRegistrationClosedTx(UUID roomId, Instant now) {
        Room room = roomRepository.findById(roomId).orElse(null);
        if (room == null
                || room.getEndedAt() != null
                || room.getRegistrationClosedNotifiedAt() != null
                || room.getScope() != RoomScope.EXPERT_SESSION) {
            return;
        }
        room.setRegistrationClosedNotifiedAt(now);
        roomRepository.save(room);
        UUID sessionId = room.getExpertSessionId();
        if (sessionId == null) return;
        List<UUID> groupIds;
        try {
            groupIds = expertInternalService.findEnrolledGroupIds(sessionId);
        } catch (RuntimeException e) {
            log.warn("[RoomLifecycle] lookup enrolled groups failed for session {}: {}", sessionId, e.getMessage());
            return;
        }
        for (UUID groupId : groupIds) {
            try {
                self.postExpertSessionOpenMessageTx(groupId, sessionId);
            } catch (RuntimeException e) {
                log.warn("[RoomLifecycle] expert-session-open post failed (group {}, session {}): {}",
                        groupId, sessionId, e.getMessage());
            }
        }
    }

    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.REQUIRES_NEW)
    public void postExpertSessionOpenMessageTx(UUID groupId, UUID sessionId) {
        chatInternalService.postSystemMessageWithLink(
                groupId,
                ChatMessageType.SYSTEM_EXPERT_SESSION_OPEN,
                "Expert session is opening — chat and whiteboard are live, video starts at the scheduled time.",
                ChatLinkTargetType.EXPERT_SESSION,
                sessionId
        );
    }
}
