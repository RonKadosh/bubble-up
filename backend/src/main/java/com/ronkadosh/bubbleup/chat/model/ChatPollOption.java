package com.ronkadosh.bubbleup.chat.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Entity
@Table(
        name = "chat_poll_options",
        indexes = @Index(name = "idx_chat_poll_options_poll_pos", columnList = "poll_id, position")
)
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatPollOption {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "poll_id", nullable = false)
    private UUID pollId;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String text;

    /** Stable display order. */
    @Column(nullable = false)
    private int position;
}
