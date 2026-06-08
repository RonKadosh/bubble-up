package com.ronkadosh.bubbleup.room.application;

import com.ronkadosh.bubbleup.auth.internal.AuthInternalService;
import com.ronkadosh.bubbleup.auth.internal.dto.UserIdentity;
import com.ronkadosh.bubbleup.calendar.internal.CalendarInternalService;
import com.ronkadosh.bubbleup.calendar.internal.dto.CalendarEventSummary;
import com.ronkadosh.bubbleup.chat.internal.ChatInternalService;
import com.ronkadosh.bubbleup.chat.model.ChatLinkTargetType;
import com.ronkadosh.bubbleup.chat.model.ChatMessageType;
import com.ronkadosh.bubbleup.common.error.AppException;
import com.ronkadosh.bubbleup.common.error.ErrorCode;
import com.ronkadosh.bubbleup.common.events.BehaviorEventType;
import com.ronkadosh.bubbleup.common.events.UserBehaviorEvent;
import com.ronkadosh.bubbleup.common.websocket.WebSocketDestination;
import com.ronkadosh.bubbleup.common.websocket.WebSocketPublisher;
import com.ronkadosh.bubbleup.expert.application.WhiteboardWriterRegistry;
import com.ronkadosh.bubbleup.expert.internal.ExpertInternalService;
import com.ronkadosh.bubbleup.groups.internal.GroupInternalService;
import com.ronkadosh.bubbleup.room.model.Room;
import com.ronkadosh.bubbleup.room.model.RoomScope;
import com.ronkadosh.bubbleup.room.persistence.RoomRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class RoomCommandService {

    /** Fixed extension increment for V1. Any member can extend; can be applied repeatedly. */
    public static final Duration EXTEND_BY = Duration.ofMinutes(15);

    private final RoomRepository roomRepository;
    private final GroupInternalService groupInternalService;
    private final ExpertInternalService expertInternalService;
    private final CalendarInternalService calendarInternalService;
    private final ChatInternalService chatInternalService;
    private final AuthInternalService authInternalService;
    private final WebSocketPublisher webSocketPublisher;
    private final WhiteboardRelay whiteboardRelay;
    private final WhiteboardWriterRegistry whiteboardWriterRegistry;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional
    public void publishWhiteboardSnapshot(UUID roomId, ExcalidrawSnapshot snapshot, UUID requesterId) {
        Room room = roomRepository.findById(roomId)
                .orElseThrow(() -> new AppException(ErrorCode.ROOM_NOT_FOUND));
        requireMember(room, requesterId);
        requireWhiteboardWriter(room, requesterId);
        whiteboardRelay.put(roomId, snapshot);
        webSocketPublisher.publishToTopic(
                WebSocketDestination.roomWhiteboard(roomId),
                snapshot
        );
        // Frequent action — kept fair by a high saturation k on PUBLISHED_WHITEBOARD.
        eventPublisher.publishEvent(new UserBehaviorEvent(requesterId, BehaviorEventType.PUBLISHED_WHITEBOARD));
    }

    /**
     * Best-effort {@code SYSTEM_JOIN} into an EXPERT_SESSION room's chat the
     * first time a user successfully resolves the room. No-op for GROUP rooms
     * (their JOIN fires on bubble-join, not on per-room entry). Idempotent
     * via the chat service's dedupe flag — repeat calls for the same user are
     * silent. Called by {@code RoomController.getRoom}/{@code getRoomForEvent}
     * after the query path has cleared all gates.
     */
    @Transactional
    public void recordExpertSessionJoinIfFirst(UUID roomId, UUID userId) {
        Room room = roomRepository.findById(roomId).orElse(null);
        if (room == null) return;
        if (room.getScope() != RoomScope.EXPERT_SESSION) return;
        UUID chatRoomId = room.getChatRoomId();
        if (chatRoomId == null) return;
        UserIdentity identity = authInternalService.getIdentity(userId).orElse(null);
        String displayName = identity != null && identity.displayName() != null
                ? identity.displayName()
                : userId.toString();
        chatInternalService.postSystemMessageToRoom(
                chatRoomId,
                ChatMessageType.SYSTEM_JOIN,
                userId,
                displayName,
                true
        );
    }

    /**
     * Whiteboard write is unrestricted for GROUP rooms (any member can draw).
     * For EXPERT_SESSION rooms the host can always draw; non-hosts can draw only
     * if explicitly granted via {@link WhiteboardWriterRegistry}.
     */
    private void requireWhiteboardWriter(Room room, UUID userId) {
        if (room.getScope() != RoomScope.EXPERT_SESSION) return;
        UUID sessionId = room.getExpertSessionId();
        if (sessionId == null) return;
        if (expertInternalService.isSessionHost(sessionId, userId)) return;
        if (whiteboardWriterRegistry.isWriter(sessionId, userId)) return;
        throw new AppException(ErrorCode.EXPERT_SESSION_WHITEBOARD_READ_ONLY);
    }

    /**
     * Bumps the linked calendar event's {@code endsAt} by {@link #EXTEND_BY}.
     * Posts a system message in the bubble's chat ("Session extended by 15 min — now
     * ends at HH:mm") and resets {@code Room.endWarningSentAt} so a fresh
     * 15-min warning will fire before the new end time.
     *
     * <p>Auth: any group member can extend. Time-window enforced — can't extend
     * an already-ended room.
     */
    @Transactional
    public void extend(UUID roomId, UUID requesterId) {
        Room room = roomRepository.findById(roomId)
                .orElseThrow(() -> new AppException(ErrorCode.ROOM_NOT_FOUND));
        requireMember(room, requesterId);
        if (room.getEndedAt() != null) {
            throw new AppException(ErrorCode.ROOM_ENDED);
        }
        if (room.getCalendarEventId() == null) {
            throw new AppException(ErrorCode.ROOM_REQUIRES_STUDY_SESSION_EVENT);
        }
        // "One active session per group at a time": for GROUP rooms, reject the
        // extension if the new {@code endsAt} would push into an enrolled
        // expert-session window. The pre-bump window can't overlap (extension
        // wouldn't be requested mid-session unless someone is already inside),
        // so we only check the *new* 15-min slice being added.
        if (room.getScope() == RoomScope.GROUP && room.getGroupId() != null) {
            CalendarEventSummary current = calendarInternalService.findById(room.getCalendarEventId())
                    .orElseThrow(() -> new AppException(ErrorCode.CALENDAR_EVENT_NOT_FOUND));
            java.time.Instant addedFrom = current.endsAt();
            java.time.Instant addedTo = current.endsAt().plus(EXTEND_BY);
            if (!expertInternalService.findActiveSessionsOverlappingForGroup(
                    room.getGroupId(), addedFrom, addedTo).isEmpty()) {
                throw new AppException(ErrorCode.GROUP_SCHEDULE_CONFLICT);
            }
        }
        CalendarEventSummary updated = calendarInternalService.bumpEndsAt(
                room.getCalendarEventId(), EXTEND_BY);

        // Allow the next 15-min warning to fire against the new endsAt.
        room.setEndWarningSentAt(null);
        roomRepository.save(room);

        UUID groupId = room.getGroupId();
        if (groupId != null) {
            String content = "🕐 Session extended by " + EXTEND_BY.toMinutes() + " min — now ends at "
                    + formatLocalTime(updated.endsAt());
            chatInternalService.postSystemMessageWithLink(
                    groupId,
                    ChatMessageType.SYSTEM_ROOM_EXTENDED,
                    content,
                    ChatLinkTargetType.ROOM,
                    room.getId()
            );
        }

        webSocketPublisher.publishToTopic(
                WebSocketDestination.roomLifecycle(roomId),
                RoomLifecycleEvent.extended(roomId, updated.endsAt())
        );
    }

    private static String formatLocalTime(java.time.Instant instant) {
        if (instant == null) return "";
        // Server-rendered text — UTC. Frontend can re-format from RoomLifecycleEvent
        // if it wants the user's locale.
        return java.time.LocalTime.ofInstant(instant, java.time.ZoneOffset.UTC).withSecond(0).withNano(0).toString() + " UTC";
    }

    private void requireMember(Room room, UUID userId) {
        if (room.getScope() == RoomScope.GROUP) {
            UUID groupId = room.getGroupId();
            if (groupId == null || !groupInternalService.isMember(groupId, userId)) {
                throw new AppException(ErrorCode.NOT_GROUP_MEMBER);
            }
            return;
        }
        if (room.getScope() == RoomScope.EXPERT_SESSION) {
            UUID sessionId = room.getExpertSessionId();
            if (sessionId == null || !expertInternalService.isAuthorizedForSession(sessionId, userId)) {
                throw new AppException(ErrorCode.FORBIDDEN);
            }
            return;
        }
        throw new AppException(ErrorCode.FORBIDDEN);
    }
}
