package com.ronkadosh.bubbleup.chat.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/**
 * A poll surfaced in a chat room via a LINK message ({@code linkTargetType=POLL,
 * linkTargetId=this.id}). Options + votes live in sibling entities.
 */
@Entity
@Table(name = "chat_polls")
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatPoll {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "room_id", nullable = false)
    private UUID roomId;

    /** The LINK chat message that surfaces this poll. Unique 1:1. */
    @Column(name = "message_id", nullable = false, unique = true)
    private UUID messageId;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String question;

    @Column(name = "allow_multiple", nullable = false)
    private boolean allowMultiple;

    @Setter
    @Column(name = "closed_at")
    private Instant closedAt;

    @Column(name = "created_by_user_id", nullable = false)
    private UUID createdByUserId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
}
