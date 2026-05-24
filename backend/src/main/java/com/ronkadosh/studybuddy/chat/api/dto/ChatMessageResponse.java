package com.ronkadosh.studybuddy.chat.api.dto;

import com.ronkadosh.studybuddy.chat.model.ChatLinkTargetType;
import com.ronkadosh.studybuddy.chat.model.ChatMessage;
import com.ronkadosh.studybuddy.chat.model.ChatMessageType;

import java.time.Instant;
import java.util.UUID;

public record ChatMessageResponse(
        UUID id,
        UUID roomId,
        UUID senderId,
        String content,
        Instant sentAt,
        ChatMessageType messageType,
        UUID subjectUserId,
        ChatLinkTargetType linkTargetType,
        UUID linkTargetId
) {
    public static ChatMessageResponse from(ChatMessage message) {
        return new ChatMessageResponse(
                message.getId(),
                message.getRoomId(),
                message.getSenderId(),
                message.getContent(),
                message.getSentAt(),
                message.getMessageType(),
                message.getSubjectUserId(),
                message.getLinkTargetType(),
                message.getLinkTargetId()
        );
    }
}
