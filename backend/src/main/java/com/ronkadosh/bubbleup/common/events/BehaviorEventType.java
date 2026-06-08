package com.ronkadosh.bubbleup.common.events;

public enum BehaviorEventType {

    // ── Quiz-driven (wired from day one via POST /quiz/answers) ──────────────
    USER_ANSWERED_QUIZ_QUESTION,

    // ── Currently wired behavioral signals ───────────────────────────────────
    // Role mapping + saturation (k) per type live in config: app.matching.signals.
    CREATED_GROUP,
    ADDED_MEMBER,
    JOINED_GROUP,
    CREATED_CALENDAR_EVENT,
    UPLOADED_FILE,
    SENT_MESSAGE,
    PINNED_MESSAGE,
    CREATED_POLL,
    VOTED_IN_POLL,
    CLOSED_POLL,
    CREATED_FOLDER,
    SUBMITTED_REPORT,
    PUBLISHED_WHITEBOARD,
    APPLIED_AS_EXPERT,
    BOOKED_EXPERT,
    CREATED_EXPERT_SESSION,

    // ── Future signals (not yet wired — add publisher + a signals entry when ready) ─
    // REACTED_TO_MESSAGE: reactions are frontend-only (BubbleEmojis) — no backend API.
    // STARTED_VIDEO_CALL: only call presence exists (RoomCallPresenceService), no "start" event.
    COMPLETED_SHARED_TASK,
    STARTED_VIDEO_CALL,
    REACTED_TO_MESSAGE,
    TAGGED_EXPERT_KNOWLEDGE,
    CHALLENGED_ASSUMPTION,
    PROPOSED_CREATIVE_IDEA,
}
