package com.ronkadosh.bubbleup.report.internal;

import com.ronkadosh.bubbleup.report.internal.dto.ReportAdminDto;
import com.ronkadosh.bubbleup.report.internal.dto.ReportImage;
import com.ronkadosh.bubbleup.report.model.ReportStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

/**
 * The only surface the admin module may use to reach reports. Keeps the report
 * module's repository/entities private — mirrors {@code ExpertAdminInternalService}.
 */
public interface ReportAdminInternalService {

    Page<ReportAdminDto> listByStatus(ReportStatus status, Pageable pageable);

    /** Throws {@code REPORT_NOT_FOUND} if absent. */
    ReportAdminDto getById(UUID reportId);

    /** Streams the attachment bytes; throws {@code REPORT_IMAGE_NOT_FOUND} if none. */
    ReportImage getImage(UUID reportId);

    ReportAdminDto resolve(UUID reportId, UUID adminUserId, String note);

    ReportAdminDto dismiss(UUID reportId, UUID adminUserId, String note);

    long countPending();
}
