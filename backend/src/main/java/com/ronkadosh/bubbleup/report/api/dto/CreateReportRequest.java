package com.ronkadosh.bubbleup.report.api.dto;

import com.ronkadosh.bubbleup.report.model.ReportCategory;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreateReportRequest(
        @NotNull ReportCategory category,
        @NotBlank @Size(max = 200) String subject,
        @NotBlank @Size(max = 4000) String description
) {}
