package com.ronkadosh.studybuddy.chat.application;

import com.ronkadosh.studybuddy.chat.api.dto.ChatMessageResponse;
import com.ronkadosh.studybuddy.chat.internal.ChatInternalService;
import com.ronkadosh.studybuddy.chat.internal.dto.ChatRoomSummary;
import com.ronkadosh.studybuddy.chat.model.ChatMessage;
import com.ronkadosh.studybuddy.chat.model.ChatMessageType;
import com.ronkadosh.studybuddy.chat.model.ChatRoom;
import com.ronkadosh.studybuddy.chat.persistence.ChatMessageRepository;
import com.ronkadosh.studybuddy.chat.persistence.ChatRoomRepository;
import com.ronkadosh.studybuddy.chat.persistence.MessageReadCursorRepository;
import com.ronkadosh.studybuddy.common.datetime.TimeProvider;
import com.ronkadosh.studybuddy.common.error.AppException;
import com.ronkadosh.studybuddy.common.error.ErrorCode;
import com.ronkadosh.studybuddy.common.websocket.WebSocketDestination;
import com.ronkadosh.studybuddy.common.websocket.WebSocketPublisher;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class ChatInternalServiceImpl implements ChatInternalService {

    private final ChatRoomRepository chatRoomRepository;
    private final ChatMessageRepository chatMessageRepository;
    private final MessageReadCursorRepository messageReadCursorRepository;
    private final WebSocketPublisher webSocketPublisher;
    private final TimeProvider timeProvider;

    // @Lazy breaks the cycle: this bean is referenced (transitively) by WebSocketConfig
    // via ChatTopicSubscribeInterceptor, but only needs the publisher at runtime
    // when postSystemMessage fires — not at construction.
    public ChatInternalServiceImpl(
            ChatRoomRepository chatRoomRepository,
            ChatMessageRepository chatMessageRepository,
            MessageReadCursorRepository messageReadCursorRepository,
            @Lazy WebSocketPublisher webSocketPublisher,
            TimeProvider timeProvider) {
        this.chatRoomRepository = chatRoomRepository;
        this.chatMessageRepository = chatMessageRepository;
        this.messageReadCursorRepository = messageReadCursorRepository;
        this.webSocketPublisher = webSocketPublisher;
        this.timeProvider = timeProvider;
    }

    @Override
    @Transactional(readOnly = true)
    public List<ChatRoomSummary> getRoomsForGroup(UUID groupId) {
        return chatRoomRepository.findAllByGroupId(groupId).stream()
                .map(r -> new ChatRoomSummary(r.getId(), r.getName()))
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public boolean roomExists(UUID roomId) {
        return chatRoomRepository.existsById(roomId);
    }

    @Override
    @Transactional(readOnly = true)
    public UUID getGroupIdForRoom(UUID roomId) {
        ChatRoom room = chatRoomRepository.findById(roomId)
                .orElseThrow(() -> new AppException(ErrorCode.CHAT_ROOM_NOT_FOUND));
        return room.getGroupId();
    }

    @Override
    @Transactional
    public void deleteRoomsForGroup(UUID groupId) {
        List<ChatRoom> rooms = chatRoomRepository.findAllByGroupId(groupId);
        if (rooms.isEmpty()) return;
        List<UUID> roomIds = rooms.stream().map(ChatRoom::getId).toList();
        messageReadCursorRepository.deleteAllByRoomIdIn(roomIds);
        chatMessageRepository.deleteAllByRoomIdIn(roomIds);
        chatRoomRepository.deleteAll(rooms);
    }

    @Override
    @Transactional
    public UUID createRoomForGroup(UUID groupId, String name) {
        ChatRoom room = chatRoomRepository.save(ChatRoom.builder()
                .name(name)
                .groupId(groupId)
                .build());
        return room.getId();
    }

    @Override
    @Transactional
    public void postSystemMessage(UUID groupId, ChatMessageType type, UUID subjectUserId, String subjectDisplay) {
        Optional<ChatRoom> defaultRoom = chatRoomRepository.findAllByGroupId(groupId).stream()
                .min(Comparator.comparing(ChatRoom::getCreatedAt));
        if (defaultRoom.isEmpty()) return;   // defensive: groups always have a room, no-op if not
        ChatRoom room = defaultRoom.get();
        ChatMessage saved = chatMessageRepository.save(ChatMessage.builder()
                .roomId(room.getId())
                .senderId(null)
                .content(subjectDisplay != null ? subjectDisplay : "")
                .messageType(type)
                .subjectUserId(subjectUserId)
                .sentAt(timeProvider.now())
                .build());
        webSocketPublisher.publishToTopic(
                WebSocketDestination.chatRoom(room.getId()),
                ChatMessageResponse.from(saved)
        );
    }
}
