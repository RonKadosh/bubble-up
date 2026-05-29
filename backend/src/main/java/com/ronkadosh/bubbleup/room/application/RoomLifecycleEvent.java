package com.ronkadosh.bubbleup.room.application;

import java.time.Instant;
import java.util.UUID;

/**
 * Payload broadcast on {@code /topic/rooms/{roomId}/lifecycle} when the room's
 * state changes. Frontends use it to react in real-time — dispose the Jitsi
 * iframe on {@code ENDED}, refresh metadata on {@code EXTENDED}.
 */
public record RoomLifecycleEvent(
        Event event,
        UUID roomId,
        /** For EXTENDED: the new {@code endsAt} the session was bumped to. Null otherwise. */
        Instant endsAt
) {
    public enum Event { ENDED, EXTENDED }

    public static RoomLifecycleEvent ended(UUID roomId) {
        return new RoomLifecycleEvent(Event.ENDED, roomId, null);
    }

    public static RoomLifecycleEvent extended(UUID roomId, Instant newEndsAt) {
        return new RoomLifecycleEvent(Event.EXTENDED, roomId, newEndsAt);
    }
}
