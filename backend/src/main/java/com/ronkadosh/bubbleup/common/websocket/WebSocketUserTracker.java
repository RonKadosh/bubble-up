package com.ronkadosh.bubbleup.common.websocket;

import org.springframework.messaging.simp.user.SimpUserRegistry;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Checks whether a user has an active WebSocket session.
 * Delegates to Spring's SimpUserRegistry.
 */
@Component
public class WebSocketUserTracker {

    private final SimpUserRegistry userRegistry;

    public WebSocketUserTracker(SimpUserRegistry userRegistry) {
        this.userRegistry = userRegistry;
    }

    public boolean isUserConnected(UUID userId) {
        return userRegistry.getUser(userId.toString()) != null;
    }
}
