package com.ronkadosh.studybuddy.common.websocket;

import java.security.Principal;
import java.util.UUID;

/**
 * Principal attached to the STOMP session after CONNECT auth. The name is the
 * user's UUID as a string so WebSocketPublisher.publishToUser(userId, ...) routes
 * to the right session.
 */
public record StompPrincipal(UUID userId) implements Principal {
    @Override
    public String getName() {
        return userId.toString();
    }
}
