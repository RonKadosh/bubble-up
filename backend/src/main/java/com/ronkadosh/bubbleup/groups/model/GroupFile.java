package com.ronkadosh.bubbleup.groups.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(
        name = "group_files",
        indexes = {
                @Index(name = "idx_group_files_group", columnList = "group_id"),
                @Index(name = "idx_group_files_folder", columnList = "folder_id")
        },
        uniqueConstraints = @UniqueConstraint(
                name = "uk_group_files_storage",
                columnNames = {"file_id"}
        )
)
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GroupFile {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "group_id", nullable = false)
    private UUID groupId;

    @Column(name = "uploader_id", nullable = false)
    private UUID uploaderId;

    /** Null = file lives at root of the group's file system. */
    @Column(name = "folder_id")
    private UUID folderId;

    @Column(name = "file_id", nullable = false)
    private String fileId;

    @Column(nullable = false, length = 255)
    private String originalName;

    @Column(nullable = false, length = 255)
    private String contentType;

    @Column(nullable = false)
    private long sizeBytes;

    @Column(nullable = false, updatable = false)
    private Instant uploadedAt;

    @PrePersist
    void onCreate() {
        if (uploadedAt == null) uploadedAt = Instant.now();
    }
}
