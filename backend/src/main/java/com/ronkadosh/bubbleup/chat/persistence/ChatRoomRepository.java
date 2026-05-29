package com.ronkadosh.bubbleup.chat.persistence;

import com.ronkadosh.bubbleup.chat.model.ChatRoom;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface ChatRoomRepository extends JpaRepository<ChatRoom, UUID> {

    List<ChatRoom> findAllByGroupId(UUID groupId);

    List<ChatRoom> findAllByGroupIdIn(Collection<UUID> groupIds);

    java.util.Optional<ChatRoom> findByExpertSessionId(UUID expertSessionId);
}
