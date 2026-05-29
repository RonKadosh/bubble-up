package com.ronkadosh.bubbleup.chat.application;

import com.ronkadosh.bubbleup.auth.internal.AuthInternalService;
import com.ronkadosh.bubbleup.auth.internal.dto.UserIdentity;
import com.ronkadosh.bubbleup.chat.api.dto.ChatMessageResponse;
import com.ronkadosh.bubbleup.chat.api.dto.ChatRoomResponse;
import com.ronkadosh.bubbleup.chat.api.dto.CreateRoomRequest;
import com.ronkadosh.bubbleup.chat.api.dto.SendMessageRequest;
import com.ronkadosh.bubbleup.chat.internal.ChatInternalService;
import com.ronkadosh.bubbleup.chat.model.ChatLinkTargetType;
import com.ronkadosh.bubbleup.chat.model.ChatMessage;
import com.ronkadosh.bubbleup.chat.model.ChatMessageType;
import com.ronkadosh.bubbleup.chat.model.ChatRoom;
import com.ronkadosh.bubbleup.chat.model.MessageReadCursor;
import com.ronkadosh.bubbleup.chat.persistence.ChatMessageRepository;
import com.ronkadosh.bubbleup.chat.persistence.ChatRoomRepository;
import com.ronkadosh.bubbleup.chat.persistence.MessageReadCursorRepository;
import com.ronkadosh.bubbleup.common.datetime.TimeProvider;
import com.ronkadosh.bubbleup.common.error.AppException;
import com.ronkadosh.bubbleup.common.error.ErrorCode;
import com.ronkadosh.bubbleup.common.websocket.WebSocketDestination;
import com.ronkadosh.bubbleup.common.websocket.WebSocketPublisher;
import com.ronkadosh.bubbleup.common.events.BehaviorEventType;
import com.ronkadosh.bubbleup.common.events.UserBehaviorEvent;
import com.ronkadosh.bubbleup.groups.internal.GroupInternalService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.EnumSet;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ChatCommandService {

    private static final Set<ChatLinkTargetType> SUPPORTED_LINK_TARGETS =
            EnumSet.of(ChatLinkTargetType.CALENDAR_EVENT, ChatLinkTargetType.POLL, ChatLinkTargetType.FILE);

    private final ChatRoomRepository chatRoomRepository;
    private final ChatMessageRepository chatMessageRepository;
    private final MessageReadCursorRepository messageReadCursorRepository;
    private final GroupInternalService groupInternalService;
    private final ChatInternalService chatInternalService;
    private final WebSocketPublisher webSocketPublisher;
    private final TimeProvider timeProvider;
    private final ApplicationEventPublisher eventPublisher;
    private final AuthInternalService authInternalService;

    @Transactional
    public ChatRoomResponse createRoom(CreateRoomRequest request, UUID requesterId) {
        if (!groupInternalService.groupExists(request.groupId())) {
            throw new AppException(ErrorCode.GROUP_NOT_FOUND);
        }
        if (!groupInternalService.isMember(request.groupId(), requesterId)) {
            throw new AppException(ErrorCode.NOT_GROUP_MEMBER);
        }
        ChatRoom room = ChatRoom.builder()
                .name(request.name())
                .groupId(request.groupId())
                .build();
        chatRoomRepository.save(room);
        return ChatRoomResponse.from(room);
    }

    @Transactional
    public ChatMessageResponse sendMessage(UUID roomId, UUID senderId, SendMessageRequest request) {
        chatInternalService.assertRoomAccess(roomId, senderId);

        ChatMessageType type = request.type() == null ? ChatMessageType.TEXT : request.type();
        validate(type, request);

        UUID replyToId = resolveReplyTarget(request.replyToMessageId(), roomId);

        ChatMessage message = chatMessageRepository.save(ChatMessage.builder()
                .roomId(roomId)
                .senderId(senderId)
                .content(request.content() == null ? "" : request.content())
                .messageType(type)
                .linkTargetType(type == ChatMessageType.LINK ? request.linkTargetType() : null)
                .linkTargetId(type == ChatMessageType.LINK ? request.linkTargetId() : null)
                .replyToMessageId(replyToId)
                .sentAt(timeProvider.now())
                .build());
        UserIdentity senderIdentity = authInternalService.getIdentity(senderId).orElse(null);
        ChatMessageResponse response = ChatMessageResponse.from(message, senderIdentity);
        webSocketPublisher.publishToTopic(WebSocketDestination.chatRoom(roomId), response);
        if (type == ChatMessageType.TEXT) {
            eventPublisher.publishEvent(new UserBehaviorEvent(senderId, BehaviorEventType.SENT_MESSAGE));
        }
        return response;
    }

    private UUID resolveReplyTarget(UUID replyToMessageId, UUID roomId) {
        if (replyToMessageId == null) return null;
        ChatMessage parent = chatMessageRepository.findById(replyToMessageId)
                .orElseThrow(() -> new AppException(ErrorCode.CHAT_MESSAGE_NOT_FOUND));
        if (!parent.getRoomId().equals(roomId)) {
            throw new AppException(ErrorCode.CHAT_MESSAGE_NOT_IN_ROOM);
        }
        return parent.getId();
    }

    @Transactional
    public com.ronkadosh.bubbleup.chat.api.dto.PinUpdateEvent pinMessage(UUID roomId, UUID messageId, UUID userId) {
        ChatMessage msg = loadMessageInRoom(roomId, messageId, userId);
        if (!msg.isPinned()) {
            msg.setPinned(true);
            msg.setPinnedAt(timeProvider.now());
            msg.setPinnedByUserId(userId);
            chatMessageRepository.save(msg);
        }
        com.ronkadosh.bubbleup.chat.api.dto.PinUpdateEvent event =
                new com.ronkadosh.bubbleup.chat.api.dto.PinUpdateEvent(
                        msg.getId(), true, msg.getPinnedAt(), msg.getPinnedByUserId());
        webSocketPublisher.publishToTopic(WebSocketDestination.chatRoomPins(roomId), event);
        return event;
    }

    @Transactional
    public com.ronkadosh.bubbleup.chat.api.dto.PinUpdateEvent unpinMessage(UUID roomId, UUID messageId, UUID userId) {
        ChatMessage msg = loadMessageInRoom(roomId, messageId, userId);
        if (msg.isPinned()) {
            msg.setPinned(false);
            msg.setPinnedAt(null);
            msg.setPinnedByUserId(null);
            chatMessageRepository.save(msg);
        }
        com.ronkadosh.bubbleup.chat.api.dto.PinUpdateEvent event =
                new com.ronkadosh.bubbleup.chat.api.dto.PinUpdateEvent(msg.getId(), false, null, null);
        webSocketPublisher.publishToTopic(WebSocketDestination.chatRoomPins(roomId), event);
        return event;
    }

    private ChatMessage loadMessageInRoom(UUID roomId, UUID messageId, UUID userId) {
        chatInternalService.assertRoomAccess(roomId, userId);
        ChatMessage msg = chatMessageRepository.findById(messageId)
                .orElseThrow(() -> new AppException(ErrorCode.CHAT_MESSAGE_NOT_FOUND));
        if (!msg.getRoomId().equals(roomId)) {
            throw new AppException(ErrorCode.CHAT_MESSAGE_NOT_IN_ROOM);
        }
        return msg;
    }

    @Transactional
    public void markRead(UUID roomId, UUID userId, UUID lastReadMessageId) {
        chatInternalService.assertRoomAccess(roomId, userId);
        if (lastReadMessageId == null) {
            throw new AppException(ErrorCode.VALIDATION_ERROR, "lastReadMessageId required");
        }
        MessageReadCursor cursor = messageReadCursorRepository.findByRoomIdAndUserId(roomId, userId)
                .orElseGet(() -> MessageReadCursor.builder()
                        .roomId(roomId)
                        .userId(userId)
                        .build());
        cursor.setLastReadMessageId(lastReadMessageId);
        cursor.setUpdatedAt(timeProvider.now());
        messageReadCursorRepository.save(cursor);
    }

    private void validate(ChatMessageType type, SendMessageRequest req) {
        switch (type) {
            case TEXT -> {
                if (req.content() == null || req.content().isBlank()) {
                    throw new AppException(ErrorCode.VALIDATION_ERROR, "content required for TEXT");
                }
                if (req.linkTargetType() != null || req.linkTargetId() != null) {
                    throw new AppException(ErrorCode.VALIDATION_ERROR, "link fields not allowed on TEXT");
                }
            }
            case LINK -> {
                if (req.linkTargetType() == null) {
                    throw new AppException(ErrorCode.VALIDATION_ERROR, "linkTargetType required for LINK");
                }
                if (req.linkTargetId() == null) {
                    throw new AppException(ErrorCode.VALIDATION_ERROR, "linkTargetId required for LINK");
                }
                if (!SUPPORTED_LINK_TARGETS.contains(req.linkTargetType())) {
                    throw new AppException(ErrorCode.VALIDATION_ERROR,
                            "unsupported link target type: " + req.linkTargetType());
                }
            }
            case SYSTEM_JOIN, SYSTEM_LEAVE ->
                    throw new AppException(ErrorCode.VALIDATION_ERROR,
                            "system messages cannot be sent via HTTP");
        }
    }
}
