package com.ronkadosh.bubbleup.expert.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(
        name = "expert_session_group_enrollments",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_expert_session_group",
                columnNames = {"expert_session_id", "group_id"}
        ),
        indexes = {
                @Index(name = "idx_essge_session", columnList = "expert_session_id"),
                @Index(name = "idx_essge_group", columnList = "group_id")
        }
)
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExpertSessionGroupEnrollment {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "expert_session_id", nullable = false)
    private UUID expertSessionId;

    @Column(name = "group_id", nullable = false)
    private UUID groupId;

    @Column(name = "enrolled_by", nullable = false)
    private UUID enrolledBy;

    @Column(name = "enrolled_at", nullable = false, updatable = false)
    private Instant enrolledAt;

    @PrePersist
    void onCreate() {
        if (enrolledAt == null) enrolledAt = Instant.now();
    }
}
