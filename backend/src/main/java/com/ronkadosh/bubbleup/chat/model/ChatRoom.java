package com.ronkadosh.bubbleup.chat.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(
        name = "chat_rooms",
        indexes = {
                @Index(name = "idx_chat_rooms_group", columnList = "group_id"),
                @Index(name = "idx_chat_rooms_expert_session", columnList = "expert_session_id")
        }
)
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatRoom {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private String name;

    /**
     * Exactly one of {@code groupId} / {@code expertSessionId} must be non-null.
     * Application-level invariant enforced by callers (see {@code ChatInternalService}
     * and {@code ChatCommandService}); not a DB check constraint because H2 in
     * PG-compat mode + JPA ddl-auto won't materialize it consistently across
     * the dev / test profiles.
     */
    @Column(name = "group_id")
    private UUID groupId;

    @Column(name = "expert_session_id")
    private UUID expertSessionId;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }
}
