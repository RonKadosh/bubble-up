package com.ronkadosh.bubbleup.common.events;

public enum BehaviorEventType {

    // ── Quiz-driven (wired from day one via POST /quiz/answers) ──────────────
    USER_ANSWERED_QUIZ_QUESTION,

    // ── Currently wired behavioral signals ───────────────────────────────────
    CREATED_GROUP,
    ADDED_MEMBER,
    CREATED_CALENDAR_EVENT,
    UPLOADED_FILE,
    SENT_MESSAGE,

    // ── Future signals (not yet wired — add publisher + delta entry when ready) ─
    COMPLETED_SHARED_TASK,
    STARTED_VIDEO_CALL,
    REACTED_TO_MESSAGE,
    TAGGED_EXPERT_KNOWLEDGE,
    CHALLENGED_ASSUMPTION,
    PROPOSED_CREATIVE_IDEA,
}
