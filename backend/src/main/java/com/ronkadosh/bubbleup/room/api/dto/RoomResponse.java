package com.ronkadosh.bubbleup.room.api.dto;

import com.ronkadosh.bubbleup.calendar.internal.dto.CalendarEventSummary;
import com.ronkadosh.bubbleup.common.config.JitsiProperties;
import com.ronkadosh.bubbleup.room.model.Room;
import com.ronkadosh.bubbleup.room.model.RoomScope;

import java.time.Instant;
import java.util.UUID;

public record RoomResponse(
        UUID id,
        RoomScope scope,
        UUID calendarEventId,
        UUID groupId,
        UUID expertSessionId,
        UUID chatRoomId,
        String jitsiRoomName,
        String jitsiServerUrl,
        String jitsiAppId,
        /**
         * Jitsi JWT for joining the video room. Null when {@code videoOpensAt}
         * is in the future — the frontend renders a countdown placeholder
         * instead of mounting the iframe.
         */
        String jitsiJwt,
        boolean whiteboardEnabled,
        Instant startsAt,
        Instant endsAt,
        /**
         * When the Video cell becomes joinable. For GROUP-scoped rooms this
         * equals the join window opening ({@code startsAt - 15min}). For
         * EXPERT_SESSION-scoped rooms this equals {@code startsAt} — chat and
         * whiteboard open 5 min earlier, video opens at start.
         */
        Instant videoOpensAt,
        Instant createdAt
) {
    public static RoomResponse of(Room room, CalendarEventSummary event, JitsiProperties jitsi, String jwt, Instant videoOpensAt) {
        return new RoomResponse(
                room.getId(),
                room.getScope(),
                room.getCalendarEventId(),
                room.getGroupId(),
                room.getExpertSessionId(),
                room.getChatRoomId(),
                room.getJitsiRoomName(),
                jitsi != null ? jitsi.serverUrl() : null,
                jitsi != null ? jitsi.appId() : null,
                jwt,
                room.isWhiteboardEnabled(),
                event != null ? event.startsAt() : null,
                event != null ? event.endsAt() : null,
                videoOpensAt,
                room.getCreatedAt()
        );
    }
}
