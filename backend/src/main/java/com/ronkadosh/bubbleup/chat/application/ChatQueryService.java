package com.ronkadosh.bubbleup.chat.application;

import com.ronkadosh.bubbleup.auth.internal.AuthInternalService;
import com.ronkadosh.bubbleup.auth.internal.dto.UserIdentity;
import com.ronkadosh.bubbleup.chat.api.dto.ChatMessageResponse;
import com.ronkadosh.bubbleup.chat.api.dto.ChatRoomResponse;
import com.ronkadosh.bubbleup.chat.internal.ChatInternalService;
import com.ronkadosh.bubbleup.chat.model.ChatMessage;
import com.ronkadosh.bubbleup.chat.model.ChatMessageType;
import com.ronkadosh.bubbleup.chat.model.ChatRoom;
import com.ronkadosh.bubbleup.chat.model.MessageReadCursor;
import com.ronkadosh.bubbleup.chat.persistence.ChatMessageRepository;
import com.ronkadosh.bubbleup.chat.persistence.ChatRoomRepository;
import com.ronkadosh.bubbleup.chat.persistence.MessageReadCursorRepository;
import com.ronkadosh.bubbleup.common.error.AppException;
import com.ronkadosh.bubbleup.common.error.ErrorCode;
import com.ronkadosh.bubbleup.groups.internal.GroupInternalService;
import com.ronkadosh.bubbleup.groups.internal.dto.GroupSummary;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
    private final ChatInternalService chatInternalService;
    private final AuthInternalService authInternalService;

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
                    // Only human-authored content counts as unread — SYSTEM_* notices are
                    // rendered inline, never as an unread badge (see ChatMessageType.CONTENT_TYPES).
                    if (cursor == null) {
                        unread = chatMessageRepository.countByRoomIdAndMessageTypeIn(
                                r.getId(), ChatMessageType.CONTENT_TYPES);
                    } else {
                        ChatMessage cursorMsg = cursorMessagesById.get(cursor.getLastReadMessageId());
                        if (cursorMsg == null) {
                            // Cursor points at a missing message — treat as no cursor.
                            unread = chatMessageRepository.countByRoomIdAndMessageTypeIn(
                                    r.getId(), ChatMessageType.CONTENT_TYPES);
                        } else {
                            unread = chatMessageRepository.countByRoomIdAndSentAtGreaterThanAndMessageTypeIn(
                                    r.getId(), cursorMsg.getSentAt(), ChatMessageType.CONTENT_TYPES);
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
        return enrich(rows);
    }

    /**
     * Returns a page of messages centered on {@code focusId}: half (rounded up) at or before
     * the focus, half strictly after. Result is DESC by sentAt so the client can merge it
     * into its existing message list with the same ordering as {@link #getMessages}.
     */
    @Transactional(readOnly = true)
    public List<ChatMessageResponse> getMessagesAround(UUID roomId, UUID requesterId, UUID focusId, Integer size) {
        findRoomAndRequireMember(roomId, requesterId);
        ChatMessage focus = chatMessageRepository.findById(focusId)
                .orElseThrow(() -> new AppException(ErrorCode.CHAT_MESSAGE_NOT_FOUND));
        if (!focus.getRoomId().equals(roomId)) {
            throw new AppException(ErrorCode.CHAT_MESSAGE_NOT_IN_ROOM);
        }
        int clamped = clampSize(size);
        int before = (clamped + 1) / 2;
        int after = clamped - before;

        List<ChatMessage> beforeRows = chatMessageRepository.findAtOrBeforeFocus(
                roomId, focus.getSentAt(), focus.getId(), PageRequest.of(0, before));
        List<ChatMessage> afterRows = chatMessageRepository.findAfterFocus(
                roomId, focus.getSentAt(), focus.getId(), PageRequest.of(0, after));

        // beforeRows is DESC; afterRows is ASC. Compose DESC: newer (reversed) + older.
        List<ChatMessage> combined = new ArrayList<>(beforeRows.size() + afterRows.size());
        for (int i = afterRows.size() - 1; i >= 0; i--) combined.add(afterRows.get(i));
        combined.addAll(beforeRows);
        return enrich(combined);
    }

    @Transactional(readOnly = true)
    public List<ChatMessageResponse> getPinnedMessages(UUID roomId, UUID requesterId) {
        findRoomAndRequireMember(roomId, requesterId);
        return enrich(chatMessageRepository.findAllByRoomIdAndPinnedTrueOrderByPinnedAtDesc(roomId));
    }

    /**
     * Embed sender identity (display name + avatar URL) into each row. One batch
     * lookup against {@code auth.users} per page, regardless of how many distinct
     * senders the page contains. System messages (null senderId) get null identity.
     */
    private List<ChatMessageResponse> enrich(List<ChatMessage> rows) {
        if (rows.isEmpty()) return List.of();
        Set<UUID> senderIds = new HashSet<>();
        for (ChatMessage m : rows) {
            if (m.getSenderId() != null) senderIds.add(m.getSenderId());
        }
        Map<UUID, UserIdentity> byId = senderIds.isEmpty()
                ? Map.of()
                : authInternalService.getIdentitiesByIds(senderIds);
        List<ChatMessageResponse> out = new ArrayList<>(rows.size());
        for (ChatMessage m : rows) {
            UserIdentity identity = m.getSenderId() == null ? null : byId.get(m.getSenderId());
            out.add(ChatMessageResponse.from(m, identity));
        }
        return out;
    }

    private ChatRoom findRoomAndRequireMember(UUID roomId, UUID requesterId) {
        chatInternalService.assertRoomAccess(roomId, requesterId);
        return chatRoomRepository.findById(roomId)
                .orElseThrow(() -> new AppException(ErrorCode.CHAT_ROOM_NOT_FOUND));
    }

    private static int clampSize(Integer size) {
        if (size == null) return DEFAULT_PAGE_SIZE;
        if (size < 1) return 1;
        return Math.min(MAX_PAGE_SIZE, size);
    }
}
