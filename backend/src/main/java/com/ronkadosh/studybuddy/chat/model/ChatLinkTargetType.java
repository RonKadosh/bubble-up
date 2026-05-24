package com.ronkadosh.studybuddy.chat.model;

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
    CALENDAR_EVENT
}
