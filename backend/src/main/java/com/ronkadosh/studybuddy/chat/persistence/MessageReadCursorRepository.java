package com.ronkadosh.studybuddy.chat.persistence;

import com.ronkadosh.studybuddy.chat.model.MessageReadCursor;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface MessageReadCursorRepository extends JpaRepository<MessageReadCursor, UUID> {

    Optional<MessageReadCursor> findByRoomIdAndUserId(UUID roomId, UUID userId);

    List<MessageReadCursor> findAllByUserIdAndRoomIdIn(UUID userId, Collection<UUID> roomIds);

    @Modifying
    @Query("delete from MessageReadCursor c where c.roomId in :roomIds")
    int deleteAllByRoomIdIn(@Param("roomIds") Collection<UUID> roomIds);
}
