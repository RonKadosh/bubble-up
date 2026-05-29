package com.ronkadosh.bubbleup.common.websocket;

import org.springframework.messaging.support.ChannelInterceptor;

/**
 * Marker for inbound STOMP channel interceptors. WebSocketConfig auto-collects all
 * beans implementing this and registers them in @Order. Use @Order(1) for auth,
 * @Order(2+) for per-destination authorization.
 */
public interface WsChannelInterceptor extends ChannelInterceptor {
}
