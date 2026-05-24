package com.ronkadosh.studybuddy.chat.persistence;

import com.ronkadosh.studybuddy.chat.model.ChatMessage;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

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

    long countByRoomId(UUID roomId);

    @Modifying
    @Query("delete from ChatMessage m where m.roomId in :roomIds")
    int deleteAllByRoomIdIn(@Param("roomIds") Collection<UUID> roomIds);
}
