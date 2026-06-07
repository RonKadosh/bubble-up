package com.ronkadosh.bubbleup.report.api.dto;

import com.ronkadosh.bubbleup.report.model.Report;
import com.ronkadosh.bubbleup.report.model.ReportCategory;
import com.ronkadosh.bubbleup.report.model.ReportStatus;

import java.time.Instant;
import java.util.UUID;

/** What the reporter gets back after submitting — no internal review fields. */
public record ReportResponse(
        UUID id,
        ReportCategory category,
        String subject,
        String description,
        ReportStatus status,
        boolean hasAttachment,
        Instant createdAt
) {
    public static ReportResponse from(Report r) {
        return new ReportResponse(
                r.getId(),
                r.getCategory(),
                r.getSubject(),
                r.getDescription(),
                r.getStatus(),
                r.hasAttachment(),
                r.getCreatedAt()
        );
    }
}
