package com.ronkadosh.bubbleup.expert.internal;

import com.ronkadosh.bubbleup.expert.internal.dto.ExpertProfileSummary;
import com.ronkadosh.bubbleup.expert.internal.dto.ExpertSessionSummary;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Cross-module surface for the expert module.
 */
public interface ExpertInternalService {

    boolean isVerifiedExpert(UUID userId);

    Optional<ExpertProfileSummary> findProfileByUser(UUID userId);

    Optional<ExpertSessionSummary> findSessionById(UUID sessionId);

    /**
     * True iff {@code userId} is the host of the session.
     */
    boolean isSessionHost(UUID sessionId, UUID userId);

    /**
     * True iff {@code userId} is the host OR a member of a group that is
     * enrolled in this session. No time-window check — for chat history
     * access, listing sessions, etc.
     */
    boolean isAuthorizedForSession(UUID sessionId, UUID userId);

    /**
     * Same authorization as {@link #isAuthorizedForSession(UUID, UUID)}, looked
     * up from the calendar event that backs an expert session.
     */
    boolean isAuthorizedForSessionCalendarEvent(UUID calendarEventId, UUID userId);

    /**
     * True iff {@code isAuthorizedForSession} AND the current time is within
     * {@code [startsAt - 15min, endsAt]}. Use this to gate entry to the
     * live video/whiteboard room.
     */
    boolean canEnterRoomNow(UUID sessionId, UUID userId);

    /**
     * Calendar event ids for live sessions a group is enrolled in. Excludes
     * sessions with status {@code CANCELLED} or {@code ENDED}. Used by the
     * calendar module to merge session events into group calendars without
     * duplicating event rows.
     */
    List<UUID> findActiveSessionCalendarEventIdsForGroup(UUID groupId);

    /**
     * Live (non-cancelled, non-ended) sessions a group is enrolled in whose
     * time window overlaps {@code [startsAt, endsAt)}. Used by the
     * "one active session per group at a time" rule when scheduling /
     * extending a Bubble Room. Half-open interval — a session ending at
     * {@code startsAt} does not conflict.
     */
    List<ExpertSessionSummary> findActiveSessionsOverlappingForGroup(
            UUID groupId, Instant startsAt, Instant endsAt);

    /**
     * Group ids enrolled in a given session. Used by the room lifecycle
     * scheduler to broadcast the {@code SYSTEM_EXPERT_SESSION_OPEN}
     * notification into each group's default chat at registration-close time.
     */
    List<UUID> findEnrolledGroupIds(UUID sessionId);
}
