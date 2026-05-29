package com.ronkadosh.bubbleup.expert.model;

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
        name = "expert_sessions",
        indexes = {
                @Index(name = "idx_expert_sessions_expert", columnList = "expert_profile_id"),
                @Index(name = "idx_expert_sessions_calendar", columnList = "calendar_event_id", unique = true),
                @Index(name = "idx_expert_sessions_status", columnList = "status")
        }
)
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExpertSession {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "expert_profile_id", nullable = false)
    private UUID expertProfileId;

    /**
     * Denormalized for cheap lookup; the user id is the canonical "host" identity
     * and is what room/whiteboard/Jitsi gating compares against.
     */
    @Column(name = "expert_user_id", nullable = false)
    private UUID expertUserId;

    @Column(name = "calendar_event_id", nullable = false, unique = true)
    private UUID calendarEventId;

    @Column(nullable = false, length = 140)
    @Setter
    private String title;

    @Column(length = 2000)
    @Setter
    private String description;

    /** Max number of enrolled groups. */
    @Column(nullable = false)
    @Setter
    private int capacity;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    @Setter
    private ExpertSessionStatus status;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    void onCreate() {
        if (createdAt == null) createdAt = Instant.now();
    }
}
