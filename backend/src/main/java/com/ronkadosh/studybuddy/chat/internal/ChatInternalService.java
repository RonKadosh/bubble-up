package com.ronkadosh.studybuddy.chat.internal;

import com.ronkadosh.studybuddy.chat.internal.dto.ChatRoomSummary;
import com.ronkadosh.studybuddy.chat.model.ChatMessageType;

import java.util.List;
import java.util.UUID;

public interface ChatInternalService {
    List<ChatRoomSummary> getRoomsForGroup(UUID groupId);
    boolean roomExists(UUID roomId);
    UUID getGroupIdForRoom(UUID roomId);
    void deleteRoomsForGroup(UUID groupId);
    /** Creates a room without membership checks. Callers are responsible for authorization. */
    UUID createRoomForGroup(UUID groupId, String name);
    /**
     * Posts a system message (no human sender, no membership check) into the group's default
     * (oldest) chat room and broadcasts it on the room's WS topic. No-op if the group has no rooms.
     * {@code subjectDisplay} is stored as the message content for cheap render (typically the
     * subject user's email).
     */
    void postSystemMessage(UUID groupId, ChatMessageType type, UUID subjectUserId, String subjectDisplay);
}
