package com.ronkadosh.bubbleup.chat.api.dto;

import com.ronkadosh.bubbleup.auth.internal.dto.UserIdentity;
import com.ronkadosh.bubbleup.chat.model.ChatLinkTargetType;
import com.ronkadosh.bubbleup.chat.model.ChatMessage;
import com.ronkadosh.bubbleup.chat.model.ChatMessageType;

import java.time.Instant;
import java.util.UUID;

public record ChatMessageResponse(
        UUID id,
        UUID roomId,
        UUID senderId,
        String senderDisplayName,
        String senderAvatarUrl,
        String content,
        Instant sentAt,
        ChatMessageType messageType,
        UUID subjectUserId,
        ChatLinkTargetType linkTargetType,
        UUID linkTargetId,
        UUID replyToMessageId,
        boolean pinned,
        Instant pinnedAt,
        UUID pinnedByUserId
) {
    /**
     * {@code identity} is null for SYSTEM_* messages (no sender) or when the
     * sender's identity isn't in the batch map (e.g. deleted user). The frontend
     * falls back to the gradient-initial Avatar in either case.
     */
    public static ChatMessageResponse from(ChatMessage message, UserIdentity identity) {
        String displayName = identity == null ? null : identity.displayName();
        String avatarUrl = identity == null ? null : identity.avatarUrl();
        return new ChatMessageResponse(
                message.getId(),
                message.getRoomId(),
                message.getSenderId(),
                displayName,
                avatarUrl,
                message.getContent(),
                message.getSentAt(),
                message.getMessageType(),
                message.getSubjectUserId(),
                message.getLinkTargetType(),
                message.getLinkTargetId(),
                message.getReplyToMessageId(),
                message.isPinned(),
                message.getPinnedAt(),
                message.getPinnedByUserId()
        );
    }
}
