package com.ronkadosh.bubbleup.demo.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/**
 * One per-visitor demo world. Tracks the lifecycle of an isolated world built by
 * {@code DemoWorldSeeder} so the idle-TTL sweep ({@code DemoCleanupService}) can
 * find and purge abandoned ones.
 *
 * <p>The whole world hangs off {@code universityId} (every demo user has this
 * university; every group's offering → course → this university), so cleanup
 * scopes its deletes by it — no per-row tagging needed.
 */
@Entity
@Table(
        name = "demo_sessions",
        uniqueConstraints = @UniqueConstraint(name = "uk_demo_sessions_token", columnNames = "session_token"),
        indexes = {
                @Index(name = "idx_demo_sessions_guest", columnList = "guest_user_id"),
                @Index(name = "idx_demo_sessions_last_seen", columnList = "last_seen_at")
        }
)
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DemoSession {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    /** Short random token that namespaces this world's globally-unique fields. */
    @Column(name = "session_token", nullable = false, length = 32, updatable = false)
    private String sessionToken;

    /** The session's own University id — the root all its data hangs off. */
    @Column(name = "university_id", nullable = false, updatable = false)
    private UUID universityId;

    /** The auto-logged-in guest ("You"). */
    @Column(name = "guest_user_id", nullable = false, updatable = false)
    private UUID guestUserId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    /** Bumped by the client heartbeat; the idle-TTL sweep compares against now. */
    @Column(name = "last_seen_at", nullable = false)
    @Setter
    private Instant lastSeenAt;

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        if (createdAt == null) createdAt = now;
        if (lastSeenAt == null) lastSeenAt = now;
    }
}
