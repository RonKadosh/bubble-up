package com.ronkadosh.studybuddy.chat.application;

import com.ronkadosh.studybuddy.chat.api.dto.ChatMessageResponse;
import com.ronkadosh.studybuddy.chat.api.dto.ChatRoomResponse;
import com.ronkadosh.studybuddy.chat.api.dto.CreateRoomRequest;
import com.ronkadosh.studybuddy.chat.api.dto.SendMessageRequest;
import com.ronkadosh.studybuddy.chat.model.ChatLinkTargetType;
import com.ronkadosh.studybuddy.chat.model.ChatMessage;
import com.ronkadosh.studybuddy.chat.model.ChatMessageType;
import com.ronkadosh.studybuddy.chat.model.ChatRoom;
import com.ronkadosh.studybuddy.chat.model.MessageReadCursor;
import com.ronkadosh.studybuddy.chat.persistence.ChatMessageRepository;
import com.ronkadosh.studybuddy.chat.persistence.ChatRoomRepository;
import com.ronkadosh.studybuddy.chat.persistence.MessageReadCursorRepository;
import com.ronkadosh.studybuddy.common.datetime.TimeProvider;
import com.ronkadosh.studybuddy.common.error.AppException;
import com.ronkadosh.studybuddy.common.error.ErrorCode;
import com.ronkadosh.studybuddy.common.websocket.WebSocketDestination;
import com.ronkadosh.studybuddy.common.websocket.WebSocketPublisher;
import com.ronkadosh.studybuddy.groups.internal.GroupInternalService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.EnumSet;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ChatCommandService {

    private static final Set<ChatLinkTargetType> SUPPORTED_LINK_TARGETS =
            EnumSet.of(ChatLinkTargetType.CALENDAR_EVENT);

    private final ChatRoomRepository chatRoomRepository;
    private final ChatMessageRepository chatMessageRepository;
    private final MessageReadCursorRepository messageReadCursorRepository;
    private final GroupInternalService groupInternalService;
    private final WebSocketPublisher webSocketPublisher;
    private final TimeProvider timeProvider;

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
        ChatRoom room = chatRoomRepository.findById(roomId)
                .orElseThrow(() -> new AppException(ErrorCode.CHAT_ROOM_NOT_FOUND));
        if (!groupInternalService.isMember(room.getGroupId(), senderId)) {
            throw new AppException(ErrorCode.NOT_GROUP_MEMBER);
        }

        ChatMessageType type = request.type() == null ? ChatMessageType.TEXT : request.type();
        validate(type, request);

        ChatMessage message = chatMessageRepository.save(ChatMessage.builder()
                .roomId(roomId)
                .senderId(senderId)
                .content(request.content() == null ? "" : request.content())
                .messageType(type)
                .linkTargetType(type == ChatMessageType.LINK ? request.linkTargetType() : null)
                .linkTargetId(type == ChatMessageType.LINK ? request.linkTargetId() : null)
                .sentAt(timeProvider.now())
                .build());
        ChatMessageResponse response = ChatMessageResponse.from(message);
        webSocketPublisher.publishToTopic(WebSocketDestination.chatRoom(roomId), response);
        return response;
    }

    @Transactional
    public void markRead(UUID roomId, UUID userId, UUID lastReadMessageId) {
        ChatRoom room = chatRoomRepository.findById(roomId)
                .orElseThrow(() -> new AppException(ErrorCode.CHAT_ROOM_NOT_FOUND));
        if (!groupInternalService.isMember(room.getGroupId(), userId)) {
            throw new AppException(ErrorCode.NOT_GROUP_MEMBER);
        }
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
