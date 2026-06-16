package com.ronkadosh.bubbleup.room.application;

import com.ronkadosh.bubbleup.common.datetime.TimeProvider;
import com.ronkadosh.bubbleup.common.error.AppException;
import com.ronkadosh.bubbleup.common.error.ErrorCode;
import com.ronkadosh.bubbleup.common.websocket.StompPrincipal;
import com.ronkadosh.bubbleup.common.websocket.WebSocketDestination;
import com.ronkadosh.bubbleup.common.websocket.WebSocketPublisher;
import com.ronkadosh.bubbleup.common.websocket.WebSocketUserTracker;
import com.ronkadosh.bubbleup.expert.internal.ExpertInternalService;
import com.ronkadosh.bubbleup.groups.internal.GroupInternalService;
import com.ronkadosh.bubbleup.room.api.dto.RoomPresenceEvent;
import com.ronkadosh.bubbleup.room.model.Room;
import com.ronkadosh.bubbleup.room.model.RoomScope;
import com.ronkadosh.bubbleup.room.persistence.RoomRepository;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

import java.security.Principal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory tracker of who is currently inside each room's video call, keyed by
 * roomId. Ephemeral (lost on restart) — mirrors {@link WhiteboardRelay}.
 *
 * <p>The frontend reports JOIN/LEAVE off Jitsi's own conference events
 * ({@code RoomPresenceSocketController}). Each change re-broadcasts the live count
 * on {@link WebSocketDestination#roomPresence}; the same count is also read as a
 * snapshot by the dashboard feed and the room response.
 *
 * <p>Crash-safety: a closed tab never sends LEAVE, so {@link #onDisconnect} clears
 * the user from every call when their WebSocket fully drops.
 */
@Service
public class RoomCallPresenceService {

    private final RoomRepository roomRepository;
    private final GroupInternalService groupInternalService;
    private final ExpertInternalService expertInternalService;
    private final WebSocketPublisher webSocketPublisher;
    private final WebSocketUserTracker webSocketUserTracker;
    private final TimeProvider timeProvider;

    /** roomId → set of user IDs currently in the call. Empty sets are left in place (harmless). */
    private final Map<UUID, Set<UUID>> participantsByRoom = new ConcurrentHashMap<>();

    /**
     * roomId → the last instant the room had ≥1 occupant, stamped on every occupancy
     * change (join / leave / disconnect). When a room empties, this ≈ the moment the
     * last person left — the lifecycle scheduler uses it to apply an "empty for N min"
     * grace before closing an overtime room. In-memory, same ephemerality as
     * {@link #participantsByRoom} (lost on restart).
     */
    private final Map<UUID, Instant> lastOccupiedByRoom = new ConcurrentHashMap<>();

    // @Lazy on the WebSocket-infra beans breaks a cycle: this service is pulled in
    // by RoomInternalServiceImpl, and both WebSocketPublisher and WebSocketUserTracker
    // depend on the WebSocket broker config, which → RoomTopicSubscribeInterceptor →
    // RoomInternalService closes the loop. Both are only touched at runtime
    // (broadcast / disconnect), so lazy resolution is safe. Mirrors ChatInternalServiceImpl.
    public RoomCallPresenceService(
            RoomRepository roomRepository,
            GroupInternalService groupInternalService,
            ExpertInternalService expertInternalService,
            @Lazy WebSocketPublisher webSocketPublisher,
            @Lazy WebSocketUserTracker webSocketUserTracker,
            TimeProvider timeProvider) {
        this.roomRepository = roomRepository;
        this.groupInternalService = groupInternalService;
        this.expertInternalService = expertInternalService;
        this.webSocketPublisher = webSocketPublisher;
        this.webSocketUserTracker = webSocketUserTracker;
        this.timeProvider = timeProvider;
    }

    public void join(UUID roomId, UUID userId) {
        Room room = roomRepository.findById(roomId)
                .orElseThrow(() -> new AppException(ErrorCode.ROOM_NOT_FOUND));
        requireMember(room, userId);
        if (participantsByRoom.computeIfAbsent(roomId, k -> ConcurrentHashMap.newKeySet()).add(userId)) {
            touchOccupancy(roomId);
            broadcast(roomId);
        }
    }

    public void leave(UUID roomId, UUID userId) {
        Set<UUID> set = participantsByRoom.get(roomId);
        if (set != null && set.remove(userId)) {
            touchOccupancy(roomId);
            broadcast(roomId);
        }
    }

    public int count(UUID roomId) {
        Set<UUID> set = participantsByRoom.get(roomId);
        return set == null ? 0 : set.size();
    }

    /**
     * The last instant the room had ≥1 occupant, or {@code null} if it has never been
     * occupied this process lifetime. When empty, this ≈ when the last person left.
     */
    public Instant lastOccupiedAt(UUID roomId) {
        return lastOccupiedByRoom.get(roomId);
    }

    /** Stamp the room's last-occupancy-change instant. Called on every join/leave/disconnect. */
    private void touchOccupancy(UUID roomId) {
        lastOccupiedByRoom.put(roomId, timeProvider.now());
    }

    /**
     * When a user's WebSocket fully drops (no remaining sessions — a second open tab
     * suppresses cleanup, mirroring {@code PresenceCommandService}), remove them from
     * every call they were in and re-broadcast those rooms.
     */
    @EventListener
    public void onDisconnect(SessionDisconnectEvent event) {
        UUID userId = userIdFrom(event.getUser());
        if (userId == null) return;
        if (webSocketUserTracker.isUserConnected(userId)) return;
        List<UUID> affected = new ArrayList<>();
        for (Map.Entry<UUID, Set<UUID>> e : participantsByRoom.entrySet()) {
            if (e.getValue().remove(userId)) affected.add(e.getKey());
        }
        for (UUID roomId : affected) {
            touchOccupancy(roomId);
            broadcast(roomId);
        }
    }

    private void broadcast(UUID roomId) {
        webSocketPublisher.publishToTopic(
                WebSocketDestination.roomPresence(roomId),
                new RoomPresenceEvent(roomId, count(roomId)));
    }

    /** Same gate as {@code RoomCommandService.requireMember}. */
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

    private UUID userIdFrom(Principal principal) {
        if (principal instanceof StompPrincipal sp) return sp.userId();
        return null;
    }
}
