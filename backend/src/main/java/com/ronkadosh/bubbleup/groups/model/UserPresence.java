package com.ronkadosh.bubbleup.groups.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/**
 * Per-user presence record. One row per user; the row exists once the user has ever
 * connected over WebSocket. Combine with {@link com.ronkadosh.bubbleup.common.websocket.WebSocketUserTracker}
 * for the live online/offline truth — this table tracks the {@code lastSeenAt} timestamp.
 */
@Entity
@Table(name = "user_presence")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserPresence {

    @Id
    @Column(name = "user_id")
    private UUID userId;

    @Column(name = "last_seen_at", nullable = false)
    private Instant lastSeenAt;
}
