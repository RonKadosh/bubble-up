package com.ronkadosh.bubbleup.chat.internal.dto;

import com.ronkadosh.bubbleup.chat.model.ChatMessageType;

import java.time.Instant;
import java.util.UUID;

/**
 * A membership signal derived from a SYSTEM_JOIN / SYSTEM_LEAVE chat message.
 * Drives the "Bubble activity" feed section ("Maya joined Algorithms Bubble").
 * {@code subjectUserId} is the user who joined/left.
 */
public record MembershipEventItem(
        UUID groupId,
        UUID subjectUserId,
        ChatMessageType messageType,
        Instant sentAt
) {}
