package com.ronkadosh.studybuddy.common.websocket;

import java.util.UUID;

public final class WebSocketDestination {
    private WebSocketDestination() {}

    public static final String NOTIFICATIONS = "/queue/notifications";
    public static final String CHAT = "/topic/chat";
    public static final String SESSION_UPDATES = "/topic/sessions";

    public static String chatRoom(UUID roomId) {
        return CHAT + "/" + roomId;
    }
}
