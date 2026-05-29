package com.ronkadosh.bubbleup.chat.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(
        name = "message_read_cursors",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_mrc_room_user",
                columnNames = {"room_id", "user_id"}
        ),
        indexes = @Index(name = "idx_mrc_user", columnList = "user_id")
)
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MessageReadCursor {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "room_id", nullable = false)
    private UUID roomId;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "last_read_message_id", nullable = false)
    @Setter
    private UUID lastReadMessageId;

    @Column(name = "updated_at", nullable = false)
    @Setter
    private Instant updatedAt;
}
