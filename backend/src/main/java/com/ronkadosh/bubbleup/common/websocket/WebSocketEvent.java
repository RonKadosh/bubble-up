package com.ronkadosh.bubbleup.common.websocket;

import java.time.Instant;

public record WebSocketEvent(
        String type,
        Object payload,
        Instant timestamp
) {}
