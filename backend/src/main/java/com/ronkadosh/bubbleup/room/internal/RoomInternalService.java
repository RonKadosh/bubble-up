package com.ronkadosh.bubbleup.room.internal;

import com.ronkadosh.bubbleup.room.internal.dto.RoomSummary;

import java.util.Optional;
import java.util.UUID;

/**
 * Cross-module surface for the room module. Drives the live video / whiteboard
 * / chat shell for both GROUP-scoped study sessions and EXPERT_SESSION-scoped
 * expert sessions.
 */
public interface RoomInternalService {

    /**
     * Idempotent. If a room already exists for the event, returns its id without changes.
     * @return the room id
     */
    UUID createRoomForCalendarEvent(UUID calendarEventId, UUID createdBy);

    /**
     * No-op if no room exists for the event.
     * Also wipes the in-memory whiteboard snapshot.
     */
    void deleteRoomForCalendarEvent(UUID calendarEventId);

    /**
     * Creates a Room ({@code scope=EXPERT_SESSION}) and a fresh chat room
     * dedicated to the session. The host is {@code createdBy}; whiteboard write
     * is restricted (the host is implicitly a writer; others are granted via
     * {@code WhiteboardWriterRegistry}).
     * @return the room id
     */
    UUID createRoomForExpertSession(UUID expertSessionId, UUID calendarEventId, UUID createdBy);

    /**
     * No-op if no room exists for the session. Also wipes the in-memory
     * whiteboard snapshot and clears the writer registry.
     */
    void deleteRoomForExpertSession(UUID expertSessionId);

    Optional<RoomSummary> findRoomForCalendarEvent(UUID calendarEventId);

    Optional<RoomSummary> findRoomForExpertSession(UUID expertSessionId);

    Optional<RoomSummary> findById(UUID roomId);

    boolean roomExists(UUID roomId);
}
