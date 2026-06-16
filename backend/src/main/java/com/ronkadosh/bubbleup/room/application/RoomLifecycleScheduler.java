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
 *   <li>Past {@code endsAt} — hard-closes the room <b>only once the call has
 *       emptied</b>: while anyone is still in the call the room stays alive
 *       past its scheduled end. Once empty, it closes after
 *       {@link #OVERTIME_EMPTY_GRACE} of continuous emptiness (a never-used room
 *       closes {@code endsAt + grace}). Hard-close sets {@code endedAt}, clears
 *       the in-memory whiteboard snapshot, and broadcasts {@code ENDED} on
 *       {@code /topic/rooms/{id}/lifecycle}.</li>
 * </ul>
 *
 * Runs every 30 seconds. Occupancy comes from {@link RoomCallPresenceService}
 * (live in-call count + last-occupied stamp). The ENDED broadcast — not the JaaS
 * JWT exp — is now the lifecycle bound, since the token is allowed to outlive
 * {@code endsAt} so an occupied call isn't kicked at its scheduled end.
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

    /**
     * How long a room must stay empty (no one in the call) past {@code endsAt}
     * before the scheduler hard-closes it. Gives a brief reconnect/reload window
     * so a momentary 0-count doesn't kill an otherwise-active overtime session.
     */
    private static final Duration OVERTIME_EMPTY_GRACE = Duration.ofMinutes(2);
    /** EXPERT_SESSION rooms: how early before {@code startsAt} the
     *  registration window closes and the "session is opening" system message
     *  fires into each enrolled group's chat. */
    private static final Duration REGISTRATION_CLOSES_BEFORE = Duration.ofMinutes(5);

    private final RoomRepository roomRepository;
    private final CalendarInternalService calendarInternalService;
    private final ChatInternalService chatInternalService;
    private final ExpertInternalService expertInternalService;
    private final RoomCallPresenceService roomCallPresenceService;
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
            RoomCallPresenceService roomCallPresenceService,
            WhiteboardRelay whiteboardRelay,
            @Lazy WebSocketPublisher webSocketPublisher,
            TimeProvider timeProvider) {
        this.roomRepository = roomRepository;
        this.calendarInternalService = calendarInternalService;
        this.chatInternalService = chatInternalService;
        this.expertInternalService = expertInternalService;
        this.roomCallPresenceService = roomCallPresenceService;
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

        // Pass A: hard-close past endsAt — but only once the call has emptied for
        // OVERTIME_EMPTY_GRACE. While anyone is in the call the room stays alive past
        // its scheduled end (the user-facing "stay active as long as there are people
        // in the room" behavior). Applies to both GROUP and EXPERT_SESSION scopes.
        if (now.isAfter(event.endsAt())) {
            if (roomCallPresenceService.count(roomId) > 0) {
                return;   // occupied → keep alive past endsAt
            }
            Instant lastOccupied = roomCallPresenceService.lastOccupiedAt(roomId);
            Instant emptySince = lastOccupied != null ? lastOccupied : event.endsAt();
            Instant closeAt = emptySince.plus(OVERTIME_EMPTY_GRACE);
            if (closeAt.isBefore(event.endsAt())) {
                closeAt = event.endsAt();   // never close before the scheduled end
            }
            if (now.isAfter(closeAt)) {
                self.hardCloseTx(roomId, now);
            }
            return;
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
