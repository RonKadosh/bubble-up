package com.ronkadosh.studybuddy.chat.application;

import com.ronkadosh.studybuddy.chat.api.dto.ChatMessageResponse;
import com.ronkadosh.studybuddy.chat.api.dto.ChatRoomResponse;
import com.ronkadosh.studybuddy.chat.model.ChatMessage;
import com.ronkadosh.studybuddy.chat.model.ChatRoom;
import com.ronkadosh.studybuddy.chat.model.MessageReadCursor;
import com.ronkadosh.studybuddy.chat.persistence.ChatMessageRepository;
import com.ronkadosh.studybuddy.chat.persistence.ChatRoomRepository;
import com.ronkadosh.studybuddy.chat.persistence.MessageReadCursorRepository;
import com.ronkadosh.studybuddy.common.error.AppException;
import com.ronkadosh.studybuddy.common.error.ErrorCode;
import com.ronkadosh.studybuddy.groups.internal.GroupInternalService;
import com.ronkadosh.studybuddy.groups.internal.dto.GroupSummary;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ChatQueryService {

    private static final int DEFAULT_PAGE_SIZE = 50;
    private static final int MAX_PAGE_SIZE = 100;

    private final ChatRoomRepository chatRoomRepository;
    private final ChatMessageRepository chatMessageRepository;
    private final MessageReadCursorRepository messageReadCursorRepository;
    private final GroupInternalService groupInternalService;

    @Transactional(readOnly = true)
    public List<ChatRoomResponse> getRoomsForUser(UUID userId) {
        List<UUID> groupIds = groupInternalService.getGroupsForUser(userId).stream()
                .map(GroupSummary::id)
                .toList();
        if (groupIds.isEmpty()) return List.of();

        List<ChatRoom> rooms = chatRoomRepository.findAllByGroupIdIn(groupIds);
        if (rooms.isEmpty()) return List.of();

        List<UUID> roomIds = rooms.stream().map(ChatRoom::getId).toList();
        Map<UUID, MessageReadCursor> cursorsByRoom = new HashMap<>();
        messageReadCursorRepository.findAllByUserIdAndRoomIdIn(userId, roomIds)
                .forEach(c -> cursorsByRoom.put(c.getRoomId(), c));

        // Pre-load cursor message instants in one shot (avoid per-room re-query).
        List<UUID> cursorMsgIds = cursorsByRoom.values().stream()
                .map(MessageReadCursor::getLastReadMessageId)
                .toList();
        Map<UUID, ChatMessage> cursorMessagesById = new HashMap<>();
        if (!cursorMsgIds.isEmpty()) {
            chatMessageRepository.findAllById(cursorMsgIds)
                    .forEach(m -> cursorMessagesById.put(m.getId(), m));
        }

        return rooms.stream()
                .map(r -> {
                    MessageReadCursor cursor = cursorsByRoom.get(r.getId());
                    long unread;
                    if (cursor == null) {
                        unread = chatMessageRepository.countByRoomId(r.getId());
                    } else {
                        ChatMessage cursorMsg = cursorMessagesById.get(cursor.getLastReadMessageId());
                        if (cursorMsg == null) {
                            // Cursor points at a missing message — treat as no cursor.
                            unread = chatMessageRepository.countByRoomId(r.getId());
                        } else {
                            unread = chatMessageRepository.countByRoomIdAndSentAtGreaterThan(
                                    r.getId(), cursorMsg.getSentAt());
                        }
                    }
                    return ChatRoomResponse.from(r, unread);
                })
                .toList();
    }

    @Transactional(readOnly = true)
    public ChatRoomResponse getRoom(UUID roomId, UUID requesterId) {
        ChatRoom room = findRoomAndRequireMember(roomId, requesterId);
        return ChatRoomResponse.from(room);
    }

    /**
     * Cursor-paginated. {@code before} is the id of the oldest message already loaded; the result
     * contains the next N older messages in DESC order by {@code sentAt}. If {@code before} is null,
     * returns the latest N messages.
     */
    @Transactional(readOnly = true)
    public List<ChatMessageResponse> getMessages(UUID roomId, UUID requesterId, UUID before, Integer size) {
        findRoomAndRequireMember(roomId, requesterId);
        int clamped = clampSize(size);

        List<ChatMessage> rows;
        if (before == null) {
            rows = chatMessageRepository
                    .findAllByRoomIdOrderBySentAtDesc(roomId, PageRequest.of(0, clamped))
                    .getContent();
        } else {
            ChatMessage cursor = chatMessageRepository.findById(before)
                    .orElseThrow(() -> new AppException(ErrorCode.CHAT_CURSOR_NOT_FOUND));
            rows = chatMessageRepository.findOlderByRoomId(
                    roomId, cursor.getSentAt(), cursor.getId(), PageRequest.of(0, clamped));
        }
        return rows.stream().map(ChatMessageResponse::from).toList();
    }

    private ChatRoom findRoomAndRequireMember(UUID roomId, UUID requesterId) {
        ChatRoom room = chatRoomRepository.findById(roomId)
                .orElseThrow(() -> new AppException(ErrorCode.CHAT_ROOM_NOT_FOUND));
        if (!groupInternalService.isMember(room.getGroupId(), requesterId)) {
            throw new AppException(ErrorCode.NOT_GROUP_MEMBER);
        }
        return room;
    }

    private static int clampSize(Integer size) {
        if (size == null) return DEFAULT_PAGE_SIZE;
        if (size < 1) return 1;
        return Math.min(MAX_PAGE_SIZE, size);
    }
}
