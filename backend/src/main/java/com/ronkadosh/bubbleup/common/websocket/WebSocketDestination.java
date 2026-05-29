package com.ronkadosh.bubbleup.common.websocket;

import java.util.UUID;

public final class WebSocketDestination {
    private WebSocketDestination() {}

    public static final String NOTIFICATIONS = "/queue/notifications";
    public static final String CHAT = "/topic/chat";
    public static final String SESSION_UPDATES = "/topic/sessions";
    public static final String PRESENCE = "/topic/presence";
    public static final String ROOMS = "/topic/rooms";

    public static String chatRoom(UUID roomId) {
        return CHAT + "/" + roomId;
    }

    public static String chatRoomPins(UUID roomId) {
        return CHAT + "/" + roomId + "/pins";
    }

    public static String chatRoomPolls(UUID roomId) {
        return CHAT + "/" + roomId + "/polls";
    }

    public static String presenceGroup(UUID groupId) {
        return PRESENCE + "/" + groupId;
    }

    public static String roomWhiteboard(UUID roomId) {
        return ROOMS + "/" + roomId + "/whiteboard";
    }

    public static String roomPresence(UUID roomId) {
        return ROOMS + "/" + roomId + "/presence";
    }

    /**
     * Lifecycle events for a live room: {@code ENDED}, {@code EXTENDED}.
     * Broadcast by {@code RoomLifecycleScheduler} and the extend endpoint.
     * Subscribers (frontend room page + PersistentVideo) dispose the iframe
     * on {@code ENDED} or refresh the room metadata on {@code EXTENDED}.
     */
    public static String roomLifecycle(UUID roomId) {
        return ROOMS + "/" + roomId + "/lifecycle";
    }

    /**
     * Whiteboard writer-set updates for an expert session. The host grants /
     * revokes write access via REST; the new full writer set is published here.
     * Subscribers (the room page when scope=EXPERT_SESSION) recompute the
     * Whiteboard panel's read-only flag from the broadcast.
     */
    public static String expertSessionWriters(UUID sessionId) {
        return "/topic/expert-sessions/" + sessionId + "/writers";
    }
}
