package com.ronkadosh.bubbleup.chat.model;

/**
 * Polymorphic link target for LINK chat messages. Mirrors the calendar's
 * ownerType + ownerId pattern: chat stores the type + id and never imports
 * the target module. Resolvers live in the frontend (and, eventually, in
 * cross-module link previewers if we want server-side rendering).
 *
 * <p>Adding a new linkable thing later (SESSION, FILE, COURSE_EVENT, …) is
 * a new enum entry plus a frontend resolver branch — no schema change.
 */
public enum ChatLinkTargetType {
    CALENDAR_EVENT,
    /** Poll surfaced inline in chat. {@code linkTargetId} = {@code ChatPoll.id}. */
    POLL,
    /** Room (live video/audio session). {@code linkTargetId} = {@code Room.id}. */
    ROOM,
    /** Group file (uploaded by a member). {@code linkTargetId} = {@code GroupFile.id}. */
    FILE,
    /** Expert session. {@code linkTargetId} = {@code ExpertSession.id} (NOT the room id —
     *  the link routes to {@code /sessions/{id}} on the frontend). */
    EXPERT_SESSION
}
