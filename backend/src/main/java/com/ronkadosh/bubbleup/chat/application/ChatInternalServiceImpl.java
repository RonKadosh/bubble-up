package com.ronkadosh.bubbleup.chat.application;

import com.ronkadosh.bubbleup.chat.api.dto.ChatMessageResponse;
import com.ronkadosh.bubbleup.chat.internal.ChatInternalService;
import com.ronkadosh.bubbleup.chat.internal.dto.ChatRoomSummary;
import com.ronkadosh.bubbleup.chat.model.ChatLinkTargetType;
import com.ronkadosh.bubbleup.chat.model.ChatMessage;
import com.ronkadosh.bubbleup.chat.model.ChatMessageType;
import com.ronkadosh.bubbleup.chat.model.ChatRoom;
import com.ronkadosh.bubbleup.chat.persistence.ChatMessageRepository;
import com.ronkadosh.bubbleup.chat.persistence.ChatRoomRepository;
import com.ronkadosh.bubbleup.chat.persistence.MessageReadCursorRepository;
import com.ronkadosh.bubbleup.common.datetime.TimeProvider;
import com.ronkadosh.bubbleup.common.error.AppException;
import com.ronkadosh.bubbleup.common.error.ErrorCode;
import com.ronkadosh.bubbleup.common.websocket.WebSocketDestination;
import com.ronkadosh.bubbleup.common.websocket.WebSocketPublisher;
import com.ronkadosh.bubbleup.expert.internal.ExpertInternalService;
import com.ronkadosh.bubbleup.groups.internal.GroupInternalService;
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
    private final GroupInternalService groupInternalService;
    private final ExpertInternalService expertInternalService;

    // @Lazy on WebSocketPublisher breaks a cycle: this bean is referenced
    // (transitively) by WebSocketConfig via ChatTopicSubscribeInterceptor,
    // but only needs the publisher at runtime.
    // @Lazy on ExpertInternalService breaks the cycle: the expert module's
    // session command service calls createRoomForExpertSession on us, and
    // assertRoomAccess on us calls back into expert.isAuthorizedForSession.
    public ChatInternalServiceImpl(
            ChatRoomRepository chatRoomRepository,
            ChatMessageRepository chatMessageRepository,
            MessageReadCursorRepository messageReadCursorRepository,
            @Lazy WebSocketPublisher webSocketPublisher,
            TimeProvider timeProvider,
            GroupInternalService groupInternalService,
            @Lazy ExpertInternalService expertInternalService) {
        this.chatRoomRepository = chatRoomRepository;
        this.chatMessageRepository = chatMessageRepository;
        this.messageReadCursorRepository = messageReadCursorRepository;
        this.webSocketPublisher = webSocketPublisher;
        this.timeProvider = timeProvider;
        this.groupInternalService = groupInternalService;
        this.expertInternalService = expertInternalService;
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
    public UUID createRoomForExpertSession(UUID expertSessionId, String name) {
        ChatRoom room = chatRoomRepository.save(ChatRoom.builder()
                .name(name)
                .expertSessionId(expertSessionId)
                .build());
        return room.getId();
    }

    @Override
    @Transactional
    public void deleteRoomForExpertSession(UUID expertSessionId) {
        chatRoomRepository.findByExpertSessionId(expertSessionId).ifPresent(room -> {
            messageReadCursorRepository.deleteAllByRoomIdIn(List.of(room.getId()));
            chatMessageRepository.deleteAllByRoomIdIn(List.of(room.getId()));
            chatRoomRepository.delete(room);
        });
    }

    @Override
    @Transactional(readOnly = true)
    public void assertRoomAccess(UUID roomId, UUID userId) {
        ChatRoom room = chatRoomRepository.findById(roomId)
                .orElseThrow(() -> new AppException(ErrorCode.CHAT_ROOM_NOT_FOUND));
        if (room.getGroupId() != null) {
            if (!groupInternalService.isMember(room.getGroupId(), userId)) {
                throw new AppException(ErrorCode.NOT_GROUP_MEMBER);
            }
            return;
        }
        if (room.getExpertSessionId() != null) {
            if (!expertInternalService.isAuthorizedForSession(room.getExpertSessionId(), userId)) {
                throw new AppException(ErrorCode.FORBIDDEN);
            }
            return;
        }
        // Orphaned room with neither group nor session id — should be unreachable.
        throw new AppException(ErrorCode.CHAT_ROOM_NOT_FOUND);
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
                // System messages have no sender — identity null → frontend
                // renders centered italic per ChatMessageRow's SYSTEM_* branch.
                ChatMessageResponse.from(saved, null)
        );
    }

    @Override
    @Transactional
    public void postSystemMessageToRoom(UUID roomId,
                                        ChatMessageType type,
                                        UUID subjectUserId,
                                        String subjectDisplay,
                                        boolean dedupePerSubjectUser) {
        ChatRoom room = chatRoomRepository.findById(roomId).orElse(null);
        if (room == null) return;
        if (dedupePerSubjectUser && subjectUserId != null
                && chatMessageRepository.existsByRoomIdAndSubjectUserIdAndMessageType(roomId, subjectUserId, type)) {
            return;
        }
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
                ChatMessageResponse.from(saved, null)
        );
    }

    @Override
    @Transactional
    public void postSystemMessageWithLink(UUID groupId,
                                          ChatMessageType type,
                                          String content,
                                          ChatLinkTargetType linkTargetType,
                                          UUID linkTargetId) {
        Optional<ChatRoom> defaultRoom = chatRoomRepository.findAllByGroupId(groupId).stream()
                .min(Comparator.comparing(ChatRoom::getCreatedAt));
        if (defaultRoom.isEmpty()) return;
        ChatRoom room = defaultRoom.get();
        ChatMessage saved = chatMessageRepository.save(ChatMessage.builder()
                .roomId(room.getId())
                .senderId(null)
                .content(content != null ? content : "")
                .messageType(type)
                .linkTargetType(linkTargetType)
                .linkTargetId(linkTargetId)
                .sentAt(timeProvider.now())
                .build());
        webSocketPublisher.publishToTopic(
                WebSocketDestination.chatRoom(room.getId()),
                ChatMessageResponse.from(saved, null)
        );
    }
}
