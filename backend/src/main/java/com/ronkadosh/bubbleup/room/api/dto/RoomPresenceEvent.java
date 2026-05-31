package com.ronkadosh.bubbleup.room.api.dto;

import java.util.UUID;

/**
 * Broadcast on {@code /topic/rooms/{id}/presence} whenever the set of users in a
 * room's video call changes. {@code count} is the current number of participants.
 */
public record RoomPresenceEvent(UUID roomId, int count) {}
