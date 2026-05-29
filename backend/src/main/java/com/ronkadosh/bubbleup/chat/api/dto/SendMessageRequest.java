package com.ronkadosh.bubbleup.chat.api.dto;

import com.ronkadosh.bubbleup.chat.model.ChatLinkTargetType;
import com.ronkadosh.bubbleup.chat.model.ChatMessageType;
import jakarta.validation.constraints.Size;

import java.util.UUID;

/**
 * Cross-field validation lives in {@code ChatCommandService.sendMessage}:
 * - {@code type=TEXT} (default) requires non-blank {@code content} and forbids link fields.
 * - {@code type=LINK} requires both {@code linkTargetType} and {@code linkTargetId}; {@code content}
 *   is optional (used as caption).
 * - SYSTEM_* types are not accepted from HTTP (system-initiated only).
 * - {@code replyToMessageId} is optional; when set, must point at a message in the same room.
 */
public record SendMessageRequest(
        @Size(max = 4000) String content,
        ChatMessageType type,
        ChatLinkTargetType linkTargetType,
        UUID linkTargetId,
        UUID replyToMessageId
) {}
