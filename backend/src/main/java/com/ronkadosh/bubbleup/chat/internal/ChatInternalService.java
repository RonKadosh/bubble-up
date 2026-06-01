package com.ronkadosh.bubbleup.chat.internal;

import com.ronkadosh.bubbleup.chat.internal.dto.ChatRoomSummary;
import com.ronkadosh.bubbleup.chat.internal.dto.GroupUnreadSummary;
import com.ronkadosh.bubbleup.chat.internal.dto.MembershipEventItem;
import com.ronkadosh.bubbleup.chat.model.ChatLinkTargetType;
import com.ronkadosh.bubbleup.chat.model.ChatMessageType;

import java.util.List;
import java.util.Set;
import java.util.UUID;

public interface ChatInternalService {
    List<ChatRoomSummary> getRoomsForGroup(UUID groupId);

    /**
     * Per-group unread rollup for {@code userId} across the given groups' rooms.
     * Only groups with unread messages are returned. Drives the "Bubble activity"
     * feed section's unread cards.
     */
    List<GroupUnreadSummary> getUnreadSummaryForGroups(UUID userId, Set<UUID> groupIds);

    /**
     * Recent membership signals (SYSTEM_JOIN / SYSTEM_LEAVE), newest first, across
     * the given groups' rooms, capped at {@code limit}. Drives the "Bubble activity"
     * feed section.
     */
    List<MembershipEventItem> findRecentMembershipEventsForGroups(Set<UUID> groupIds, int limit);

    /**
     * Count of messages posted to any of the group's rooms since {@code since}.
     * Drives the matching module's "activity" trending signal.
     */
    long countMessagesForGroupSince(UUID groupId, java.time.Instant since);
    boolean roomExists(UUID roomId);
    /**
     * Returns the room's group id, or {@code null} for expert-session rooms.
     * Prefer {@link #assertRoomAccess} for membership checks.
     */
    UUID getGroupIdForRoom(UUID roomId);
    void deleteRoomsForGroup(UUID groupId);
    /** Creates a room without membership checks. Callers are responsible for authorization. */
    UUID createRoomForGroup(UUID groupId, String name);

    /**
     * Creates a fresh chat room scoped to an expert session. No membership check;
     * the caller (typically {@code RoomInternalService.createRoomForExpertSession})
     * is responsible for authorization. Used per-session — chat is not reused
     * across sessions per product requirement.
     */
    UUID createRoomForExpertSession(UUID expertSessionId, String name);

    /**
     * Deletes the chat room (and its messages / cursors) for the given expert
     * session. No-op if none exists.
     */
    void deleteRoomForExpertSession(UUID expertSessionId);

    /**
     * Throws an {@link com.ronkadosh.bubbleup.common.error.AppException} if
     * {@code userId} is not allowed to read/write the given chat room.
     * Handles both group-scoped and expert-session-scoped rooms.
     */
    void assertRoomAccess(UUID roomId, UUID userId);
    /**
     * Posts a system message (no human sender, no membership check) into the group's default
     * (oldest) chat room and broadcasts it on the room's WS topic. No-op if the group has no rooms.
     * {@code subjectDisplay} is stored as the message content for cheap render (typically the
     * subject user's email).
     */
    void postSystemMessage(UUID groupId, ChatMessageType type, UUID subjectUserId, String subjectDisplay);

    /**
     * System message variant that carries a link target (e.g. a Room for the
     * "session ends in 15 min" prompt). Same posting semantics as
     * {@link #postSystemMessage} but stores {@code linkTargetType} +
     * {@code linkTargetId} on the message so the frontend can render an
     * action card (Extend, Join, …).
     *
     * <p>{@code content} is the visible text body (e.g. "Session ends in 15 minutes").
     */
    void postSystemMessageWithLink(UUID groupId,
                                   ChatMessageType type,
                                   String content,
                                   ChatLinkTargetType linkTargetType,
                                   UUID linkTargetId);

    /**
     * Posts a system message directly into a specific chat room id (no group
     * lookup) and broadcasts it on the room's WS topic. Used by the
     * expert-session module to post {@code SYSTEM_JOIN} into the session's
     * own chat room — which has no group context.
     *
     * <p>Idempotent at the caller's discretion: if {@code dedupePerSubjectUser}
     * is true and a message of the same {@code type} already exists in the
     * room for the same {@code subjectUserId}, the call is a no-op. Use this
     * for "first join" semantics where the message should only post once per
     * user per room.
     */
    void postSystemMessageToRoom(UUID roomId,
                                 ChatMessageType type,
                                 UUID subjectUserId,
                                 String subjectDisplay,
                                 boolean dedupePerSubjectUser);
}
