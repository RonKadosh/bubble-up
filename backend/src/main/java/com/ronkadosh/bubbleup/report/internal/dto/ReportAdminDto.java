package com.ronkadosh.bubbleup.report.internal.dto;

import com.ronkadosh.bubbleup.report.model.Report;
import com.ronkadosh.bubbleup.report.model.ReportCategory;
import com.ronkadosh.bubbleup.report.model.ReportStatus;

import java.time.Instant;
import java.util.UUID;

/** Admin-facing projection of a report (carries review fields; never the raw entity). */
public record ReportAdminDto(
        UUID id,
        UUID reporterUserId,
        ReportCategory category,
        String subject,
        String description,
        boolean hasAttachment,
        ReportStatus status,
        Instant createdAt,
        Instant reviewedAt,
        UUID reviewedByUserId,
        String resolutionNote
) {
    public static ReportAdminDto from(Report r) {
        return new ReportAdminDto(
                r.getId(),
                r.getReporterUserId(),
                r.getCategory(),
                r.getSubject(),
                r.getDescription(),
                r.hasAttachment(),
                r.getStatus(),
                r.getCreatedAt(),
                r.getReviewedAt(),
                r.getReviewedByUserId(),
                r.getResolutionNote()
        );
    }
}
