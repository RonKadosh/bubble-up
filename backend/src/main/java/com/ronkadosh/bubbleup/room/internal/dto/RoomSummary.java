package com.ronkadosh.bubbleup.room.internal.dto;

import com.ronkadosh.bubbleup.room.model.RoomScope;

import java.util.UUID;

/**
 * Cross-module read shape for a {@link com.ronkadosh.bubbleup.room.model.Room}.
 * Purpose-built so callers in other modules don't import the entity.
 */
public record RoomSummary(
        UUID id,
        RoomScope scope,
        UUID groupId,
        UUID expertSessionId,
        UUID calendarEventId,
        UUID chatRoomId,
        String jitsiRoomName,
        boolean whiteboardEnabled
) {}
