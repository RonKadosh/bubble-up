package com.ronkadosh.bubbleup.chat.api.dto;

import com.ronkadosh.bubbleup.chat.model.ChatRoom;

import java.time.Instant;
import java.util.UUID;

public record ChatRoomResponse(
        UUID id,
        String name,
        UUID groupId,
        UUID expertSessionId,
        Instant createdAt,
        long unreadCount
) {
    public static ChatRoomResponse from(ChatRoom room, long unreadCount) {
        return new ChatRoomResponse(
                room.getId(),
                room.getName(),
                room.getGroupId(),
                room.getExpertSessionId(),
                room.getCreatedAt(),
                unreadCount);
    }

    /** Backward-compatible overload — defaults unreadCount to 0 for callers that don't compute it. */
    public static ChatRoomResponse from(ChatRoom room) {
        return from(room, 0L);
    }
}
