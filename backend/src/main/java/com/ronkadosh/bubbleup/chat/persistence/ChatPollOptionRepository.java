package com.ronkadosh.bubbleup.chat.persistence;

import com.ronkadosh.bubbleup.chat.model.ChatPollOption;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ChatPollOptionRepository extends JpaRepository<ChatPollOption, UUID> {

    List<ChatPollOption> findAllByPollIdOrderByPositionAsc(UUID pollId);
}
