package com.ronkadosh.bubbleup.chat.persistence;

import com.ronkadosh.bubbleup.chat.model.ChatPoll;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface ChatPollRepository extends JpaRepository<ChatPoll, UUID> {
}
