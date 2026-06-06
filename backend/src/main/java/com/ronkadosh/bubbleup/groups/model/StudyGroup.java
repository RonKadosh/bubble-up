package com.ronkadosh.bubbleup.groups.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(
        name = "study_groups",
        indexes = @Index(name = "idx_study_groups_offering", columnList = "offering_id")
)
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StudyGroup {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    @Setter
    private String name;

    @Column
    @Setter
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    @Setter
    @Builder.Default
    private GroupVisibility visibility = GroupVisibility.PUBLIC;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    @Setter
    @Builder.Default
    private GroupStatus status = GroupStatus.ACTIVE;

    @Column(name = "offering_id", nullable = false, updatable = false)
    private UUID offeringId;

    /** Max members allowed in this Bubble. Chosen once at creation (4–10); immutable. */
    @Column(name = "max_members", nullable = false, updatable = false)
    private int maxMembers;

    @Column(nullable = false, updatable = false)
    private UUID createdBy;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
        if (visibility == null) {
            visibility = GroupVisibility.PUBLIC;
        }
        if (status == null) {
            status = GroupStatus.ACTIVE;
        }
    }
}
