package com.ronkadosh.bubbleup.chat.model;

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
    SYSTEM_EXPERT_SESSION_OPEN
}
