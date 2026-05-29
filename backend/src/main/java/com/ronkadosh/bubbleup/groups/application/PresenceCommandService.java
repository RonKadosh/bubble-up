package com.ronkadosh.bubbleup.groups.application;

import com.ronkadosh.bubbleup.common.datetime.TimeProvider;
import com.ronkadosh.bubbleup.common.websocket.StompPrincipal;
import com.ronkadosh.bubbleup.common.websocket.WebSocketDestination;
import com.ronkadosh.bubbleup.common.websocket.WebSocketPublisher;
import com.ronkadosh.bubbleup.common.websocket.WebSocketUserTracker;
import com.ronkadosh.bubbleup.groups.api.dto.PresenceResponse;
import com.ronkadosh.bubbleup.groups.internal.GroupInternalService;
import com.ronkadosh.bubbleup.groups.internal.dto.GroupSummary;
import com.ronkadosh.bubbleup.groups.model.UserPresence;
import com.ronkadosh.bubbleup.groups.persistence.UserPresenceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.socket.messaging.SessionConnectedEvent;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

import java.security.Principal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Write side of presence. Listens for Spring's WebSocket session events, upserts the
 * {@code user_presence} row, and broadcasts the delta to every group the user is in.
 *
 * <p>Reads (snapshot) live on {@link PresenceQueryService}.
 */
@Service
@RequiredArgsConstructor
public class PresenceCommandService {

    private final UserPresenceRepository userPresenceRepository;
    private final GroupInternalService groupInternalService;
    private final WebSocketUserTracker webSocketUserTracker;
    private final WebSocketPublisher webSocketPublisher;
    private final TimeProvider timeProvider;

    @EventListener
    @Transactional
    public void onConnect(SessionConnectedEvent event) {
        UUID userId = userIdFrom(event.getUser());
        if (userId == null) return;
        Instant now = timeProvider.now();
        upsert(userId, now);
        broadcastToUsersGroups(userId, new PresenceResponse(userId, true, now));
    }

    @EventListener
    @Transactional
    public void onDisconnect(SessionDisconnectEvent event) {
        UUID userId = userIdFrom(event.getUser());
        if (userId == null) return;
        Instant now = timeProvider.now();
        upsert(userId, now);
        // The tracker may still see the session at the moment this fires; only broadcast
        // offline if the user has no remaining sessions. If another tab is open, suppress
        // the offline blink — the user is still around.
        boolean stillConnected = webSocketUserTracker.isUserConnected(userId);
        if (!stillConnected) {
            broadcastToUsersGroups(userId, new PresenceResponse(userId, false, now));
        }
    }

    private void upsert(UUID userId, Instant now) {
        UserPresence presence = userPresenceRepository.findById(userId)
                .orElseGet(() -> UserPresence.builder().userId(userId).lastSeenAt(now).build());
        presence.setLastSeenAt(now);
        userPresenceRepository.save(presence);
    }

    private void broadcastToUsersGroups(UUID userId, PresenceResponse payload) {
        List<GroupSummary> groups = groupInternalService.getGroupsForUser(userId);
        for (GroupSummary g : groups) {
            webSocketPublisher.publishToTopic(WebSocketDestination.presenceGroup(g.id()), payload);
        }
    }

    private UUID userIdFrom(Principal principal) {
        if (principal instanceof StompPrincipal sp) return sp.userId();
        return null;
    }
}
