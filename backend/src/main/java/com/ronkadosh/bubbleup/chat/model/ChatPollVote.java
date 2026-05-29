package com.ronkadosh.bubbleup.chat.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

/**
 * One vote = (poll, user, option). The compound unique constraint makes re-voting the
 * same option a no-op; vote diffs (multi-choice toggle) delete + insert rows.
 */
@Entity
@Table(
        name = "chat_poll_votes",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_chat_poll_votes_poll_user_option",
                columnNames = {"poll_id", "user_id", "option_id"}
        ),
        indexes = {
                @Index(name = "idx_chat_poll_votes_poll", columnList = "poll_id"),
                @Index(name = "idx_chat_poll_votes_poll_user", columnList = "poll_id, user_id")
        }
)
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatPollVote {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "poll_id", nullable = false)
    private UUID pollId;

    @Column(name = "option_id", nullable = false)
    private UUID optionId;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "voted_at", nullable = false)
    private Instant votedAt;
}
