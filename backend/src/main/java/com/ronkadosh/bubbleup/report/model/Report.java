package com.ronkadosh.bubbleup.report.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/**
 * A user-submitted report. Deliberately entity-agnostic: it carries a category
 * + free-text subject/description + an optional image attachment, rather than a
 * polymorphic target reference. The attachment is stored via the shared
 * {@code FileStorageService}; only its id + content type live here.
 */
@Entity
@Table(
        name = "reports",
        indexes = {
                @Index(name = "idx_reports_status_created", columnList = "status,created_at"),
                @Index(name = "idx_reports_reporter", columnList = "reporter_user_id")
        }
)
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Report {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "reporter_user_id", nullable = false, updatable = false)
    private UUID reporterUserId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    private ReportCategory category;

    @Column(nullable = false, length = 200)
    private String subject;

    @Column(nullable = false, length = 4000)
    private String description;

    @Column(name = "attachment_file_id")
    @Setter
    private String attachmentFileId;

    @Column(name = "attachment_content_type", length = 100)
    @Setter
    private String attachmentContentType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    @Setter
    private ReportStatus status;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "reviewed_at")
    @Setter
    private Instant reviewedAt;

    @Column(name = "reviewed_by_user_id")
    @Setter
    private UUID reviewedByUserId;

    @Column(name = "resolution_note", length = 1000)
    @Setter
    private String resolutionNote;

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
        if (status == null) {
            status = ReportStatus.PENDING;
        }
    }

    public boolean hasAttachment() {
        return attachmentFileId != null;
    }
}
