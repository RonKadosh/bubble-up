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
        name = "expert_session_booking_requests",
        indexes = {
                @Index(name = "idx_essbr_expert", columnList = "expert_user_id"),
                @Index(name = "idx_essbr_group", columnList = "group_id"),
                @Index(name = "idx_essbr_status", columnList = "status")
        }
)
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExpertSessionBookingRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "expert_user_id", nullable = false)
    private UUID expertUserId;

    @Column(name = "group_id", nullable = false)
    private UUID groupId;

    @Column(name = "requested_by", nullable = false)
    private UUID requestedBy;

    @Column(name = "proposed_starts_at", nullable = false)
    private Instant proposedStartsAt;

    @Column(name = "proposed_ends_at", nullable = false)
    private Instant proposedEndsAt;

    @Column(length = 500)
    private String message;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    @Setter
    private BookingRequestStatus status;

    /**
     * Populated by the accept flow once the booking materializes into an actual
     * session. Null for PENDING / REJECTED / WITHDRAWN rows.
     */
    @Column(name = "accepted_session_id")
    @Setter
    private UUID acceptedSessionId;

    @Column(name = "decided_at")
    @Setter
    private Instant decidedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    void onCreate() {
        if (createdAt == null) createdAt = Instant.now();
    }
}
