package com.ronkadosh.bubbleup.admin.api.dto;

import com.ronkadosh.bubbleup.report.internal.dto.ReportAdminDto;
import com.ronkadosh.bubbleup.report.model.ReportCategory;
import com.ronkadosh.bubbleup.report.model.ReportStatus;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.UUID;

public final class AdminReportDtos {
    private AdminReportDtos() {}

    public record ReportResponse(
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
        public static ReportResponse from(ReportAdminDto d) {
            return new ReportResponse(
                    d.id(), d.reporterUserId(), d.category(), d.subject(), d.description(),
                    d.hasAttachment(), d.status(), d.createdAt(),
                    d.reviewedAt(), d.reviewedByUserId(), d.resolutionNote()
            );
        }
    }

    /** Optional admin note recorded against the decision. */
    public record DecisionRequest(@Size(max = 1000) String note) {}
}
