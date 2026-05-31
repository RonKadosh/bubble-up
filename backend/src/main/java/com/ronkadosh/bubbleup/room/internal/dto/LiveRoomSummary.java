package com.ronkadosh.bubbleup.room.internal.dto;

import java.time.Instant;
import java.util.UUID;

/**
 * A group study room that is joinable right now. {@code startsAt} / {@code endsAt}
 * are resolved from the room's linked calendar event. {@code participantCount} is
 * how many users are currently in the video call. Surfaced by the dashboard feed's
 * "Live" section.
 */
public record LiveRoomSummary(
        UUID roomId,
        UUID groupId,
        UUID calendarEventId,
        Instant startsAt,
        Instant endsAt,
        int participantCount
) {}
