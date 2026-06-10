package com.ronkadosh.bubbleup.chat.model;

import java.util.EnumSet;
import java.util.Set;

public enum ChatMessageType {
    TEXT,
    SYSTEM_JOIN,
    SYSTEM_LEAVE,
    LINK,
    /**
     * Posted by the room lifecycle scheduler 15 minutes before a session's
     * {@code endsAt}. Content is the rendered warning text; the linked Room
     * is carried via {@code linkTargetType=ROOM} + {@code linkTargetId}.
     * Frontend renders an inline "Extend +15 min" CTA.
     */
    SYSTEM_ROOM_END_SOON,
    /**
     * Posted by the extend endpoint after a successful bump. Plain centered
     * info row — no CTA. Links to the room via {@code linkTargetType=ROOM}.
     */
    SYSTEM_ROOM_EXTENDED,
    /**
     * Posted by the room lifecycle scheduler at {@code expertSession.startsAt - 5min}
     * (registration window close) into each enrolled group's default chat room.
     * Carries an EXPERT_SESSION link so members can jump straight into the room
     * (chat + whiteboard live; video opens at {@code startsAt}).
     */
    SYSTEM_EXPERT_SESSION_OPEN,
    /**
     * Posted by the room lifecycle scheduler when a GROUP Bubble Room opens for
     * joining ({@code event.startsAt - GROUP_OPEN_BEFORE}) into the group's default
     * chat. The "your Bubble is live" card — carries a ROOM link so members jump
     * straight in. Idempotent via {@code Room.liveNotifiedAt}.
     */
    SYSTEM_GROUP_ROOM_OPEN,
    /**
     * Posted when ownership is transferred. {@code content} is the rendered
     * "X transferred ownership to Y" sentence; {@code subjectUserId} is the new
     * owner. Rendered as a plain centered notice (like {@code SYSTEM_ROOM_EXTENDED}).
     */
    SYSTEM_OWNERSHIP_TRANSFER;

    /**
     * Human-authored content (a real message or a deliberate share) — the only
     * types that count toward "unread". SYSTEM_* rows are notices the chat renders
     * inline (and the dashboard surfaces as activity); they must never inflate an
     * unread badge or the feed's unread rollup.
     */
    public static final Set<ChatMessageType> CONTENT_TYPES = EnumSet.of(TEXT, LINK);
}
