package com.ronkadosh.bubbleup.admin.application;

import com.ronkadosh.bubbleup.admin.api.dto.AdminReportDtos;
import com.ronkadosh.bubbleup.admin.audit.AdminAuditService;
import com.ronkadosh.bubbleup.common.context.CurrentUser;
import com.ronkadosh.bubbleup.common.pagination.PageMapper;
import com.ronkadosh.bubbleup.common.pagination.PageResponseDto;
import com.ronkadosh.bubbleup.report.internal.ReportAdminInternalService;
import com.ronkadosh.bubbleup.report.internal.dto.ReportImage;
import com.ronkadosh.bubbleup.report.model.ReportStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AdminReportService {

    private final ReportAdminInternalService reportAdmin;
    private final AdminAuditService audit;
    private final PageMapper pageMapper;

    public PageResponseDto<AdminReportDtos.ReportResponse> list(ReportStatus status, int page, int size) {
        var pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        return pageMapper.toDto(
                reportAdmin.listByStatus(status, pageable).map(AdminReportDtos.ReportResponse::from));
    }

    public AdminReportDtos.ReportResponse get(UUID reportId) {
        return AdminReportDtos.ReportResponse.from(reportAdmin.getById(reportId));
    }

    public ReportImage image(UUID reportId) {
        return reportAdmin.getImage(reportId);
    }

    public AdminReportDtos.ReportResponse resolve(UUID reportId, AdminReportDtos.DecisionRequest req, CurrentUser me) {
        var report = AdminReportDtos.ReportResponse.from(reportAdmin.resolve(reportId, me.id(), req.note()));
        audit.record("REPORT_RESOLVED", "REPORT", reportId, req.note(), null);
        return report;
    }

    public AdminReportDtos.ReportResponse dismiss(UUID reportId, AdminReportDtos.DecisionRequest req, CurrentUser me) {
        var report = AdminReportDtos.ReportResponse.from(reportAdmin.dismiss(reportId, me.id(), req.note()));
        audit.record("REPORT_DISMISSED", "REPORT", reportId, req.note(), null);
        return report;
    }
}
