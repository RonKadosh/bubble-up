package com.ronkadosh.bubbleup.chat.persistence;

import com.ronkadosh.bubbleup.chat.model.ChatMessage;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.ronkadosh.bubbleup.chat.model.ChatMessageType;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface ChatMessageRepository extends JpaRepository<ChatMessage, UUID> {

    /** First page (newest N messages) for a room. */
    Page<ChatMessage> findAllByRoomIdOrderBySentAtDesc(UUID roomId, Pageable pageable);

    /**
     * Cursor pagination: messages strictly older than the cursor (by sentAt, with id as tiebreak).
     * Excludes the cursor itself and any rows with the same instant but a lexicographically greater id.
     */
    @Query("""
            select m from ChatMessage m
            where m.roomId = :roomId
              and (m.sentAt < :cutoff
                   or (m.sentAt = :cutoff and m.id < :cursorId))
            order by m.sentAt desc, m.id desc
            """)
    List<ChatMessage> findOlderByRoomId(
            @Param("roomId") UUID roomId,
            @Param("cutoff") Instant cutoff,
            @Param("cursorId") UUID cursorId,
            Pageable pageable
    );

    long countByRoomIdAndSentAtGreaterThan(UUID roomId, Instant after);

    /** Messages across a set of rooms sent after {@code after} — group activity trending signal. */
    long countByRoomIdInAndSentAtGreaterThan(Collection<UUID> roomIds, Instant after);

    long countByRoomId(UUID roomId);

    /**
     * Unread counters: only the given message types (see {@link ChatMessageType#CONTENT_TYPES})
     * so SYSTEM_* notices never inflate an unread badge or the feed's unread rollup.
     */
    long countByRoomIdAndMessageTypeIn(UUID roomId, Collection<ChatMessageType> types);

    long countByRoomIdAndSentAtGreaterThanAndMessageTypeIn(
            UUID roomId, Instant after, Collection<ChatMessageType> types);

    /** Newest-first messages across a set of rooms. Used for feed unread-rollup ordering. */
    List<ChatMessage> findByRoomIdInOrderBySentAtDesc(Collection<UUID> roomIds, Pageable pageable);

    /**
     * Newest-first messages of the given types across a set of rooms. Used by the
     * dashboard feed to surface SYSTEM_JOIN / SYSTEM_LEAVE membership events.
     */
    List<ChatMessage> findByRoomIdInAndMessageTypeInOrderBySentAtDesc(
            Collection<UUID> roomIds, Collection<ChatMessageType> messageTypes, Pageable pageable);

    /** Pinned messages for a room, most-recently-pinned first. */
    List<ChatMessage> findAllByRoomIdAndPinnedTrueOrderByPinnedAtDesc(UUID roomId);

    /**
     * Messages at or before the focus instant (tie-break by id desc), used to assemble the
     * "before half" of a focus-centered page. Includes the focus row itself.
     */
    @Query("""
            select m from ChatMessage m
            where m.roomId = :roomId
              and (m.sentAt < :focusInstant
                   or (m.sentAt = :focusInstant and m.id <= :focusId))
            order by m.sentAt desc, m.id desc
            """)
    List<ChatMessage> findAtOrBeforeFocus(
            @Param("roomId") UUID roomId,
            @Param("focusInstant") Instant focusInstant,
            @Param("focusId") UUID focusId,
            Pageable pageable
    );

    /** Messages strictly newer than the focus instant — the "after half" of a focus page. */
    @Query("""
            select m from ChatMessage m
            where m.roomId = :roomId
              and (m.sentAt > :focusInstant
                   or (m.sentAt = :focusInstant and m.id > :focusId))
            order by m.sentAt asc, m.id asc
            """)
    List<ChatMessage> findAfterFocus(
            @Param("roomId") UUID roomId,
            @Param("focusInstant") Instant focusInstant,
            @Param("focusId") UUID focusId,
            Pageable pageable
    );

    @Modifying
    @Query("delete from ChatMessage m where m.roomId in :roomIds")
    int deleteAllByRoomIdIn(@Param("roomIds") Collection<UUID> roomIds);

    /**
     * Demo seeder only: backdate every existing message in a room (its auto-posted
     * {@code SYSTEM_JOIN} notices) to {@code sentAt}, so the seeded chat history that
     * follows reads as happening *after* people joined. {@code flushAutomatically}
     * forces the pending join inserts out before the bulk UPDATE; the JPQL UPDATE
     * intentionally bypasses the {@code updatable=false} guard on {@code sentAt}.
     */
    @Modifying(flushAutomatically = true)
    @Query("update ChatMessage m set m.sentAt = :sentAt where m.roomId = :roomId")
    int backdateRoomMessages(@Param("roomId") UUID roomId, @Param("sentAt") Instant sentAt);

    /**
     * Idempotency check for system-message posters. Used by the expert-session
     * join detector to ensure a {@code SYSTEM_JOIN} is posted at most once per
     * user per session-chat room.
     */
    boolean existsByRoomIdAndSubjectUserIdAndMessageType(
            UUID roomId, UUID subjectUserId, ChatMessageType messageType);
}
