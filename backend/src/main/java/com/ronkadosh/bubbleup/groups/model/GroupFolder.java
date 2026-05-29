package com.ronkadosh.bubbleup.groups.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

/**
 * Folder for organizing GroupFile rows. Group-scoped (no cross-group folders),
 * nestable via parentId. Sibling-name uniqueness is also enforced in the service
 * layer because NULL parentId is treated as distinct by Postgres/H2 uniqueness.
 *
 * <p>v1 forbids deleting non-empty folders, so orphan files cannot exist.
 * If a future "recursive delete" is added, it MUST re-home contained files
 * to root (set folderId = null) rather than cascade-delete — folder removal
 * should never silently lose user data.
 */
@Entity
@Table(
        name = "group_folders",
        indexes = {
                @Index(name = "idx_group_folders_group", columnList = "group_id"),
                @Index(name = "idx_group_folders_parent", columnList = "parent_id")
        },
        uniqueConstraints = @UniqueConstraint(
                name = "uk_group_folders_sibling_name",
                columnNames = {"group_id", "parent_id", "name"}
        )
)
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GroupFolder {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "group_id", nullable = false)
    private UUID groupId;

    /** Null for root-level folders. */
    @Column(name = "parent_id")
    private UUID parentId;

    @Column(nullable = false, length = 120)
    private String name;

    @Column(name = "created_by_id", nullable = false)
    private UUID createdById;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    void onCreate() {
        if (createdAt == null) createdAt = Instant.now();
    }
}
