package com.ronkadosh.bubbleup.report.application;

import com.ronkadosh.bubbleup.common.datetime.TimeProvider;
import com.ronkadosh.bubbleup.common.error.AppException;
import com.ronkadosh.bubbleup.common.error.ErrorCode;
import com.ronkadosh.bubbleup.common.file.FileStorageService;
import com.ronkadosh.bubbleup.report.internal.ReportAdminInternalService;
import com.ronkadosh.bubbleup.report.internal.dto.ReportAdminDto;
import com.ronkadosh.bubbleup.report.internal.dto.ReportImage;
import com.ronkadosh.bubbleup.report.model.Report;
import com.ronkadosh.bubbleup.report.model.ReportStatus;
import com.ronkadosh.bubbleup.report.persistence.ReportRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ReportAdminInternalServiceImpl implements ReportAdminInternalService {

    private final ReportRepository repo;
    private final FileStorageService fileStorageService;
    private final TimeProvider timeProvider;

    @Override
    @Transactional(readOnly = true)
    public Page<ReportAdminDto> listByStatus(ReportStatus status, Pageable pageable) {
        return repo.findByStatus(status, pageable).map(ReportAdminDto::from);
    }

    @Override
    @Transactional(readOnly = true)
    public ReportAdminDto getById(UUID reportId) {
        return ReportAdminDto.from(load(reportId));
    }

    @Override
    @Transactional(readOnly = true)
    public ReportImage getImage(UUID reportId) {
        Report report = load(reportId);
        if (!report.hasAttachment()) {
            throw new AppException(ErrorCode.REPORT_IMAGE_NOT_FOUND);
        }
        byte[] bytes = fileStorageService.download(report.getAttachmentFileId());
        return new ReportImage(bytes, report.getAttachmentContentType());
    }

    @Override
    @Transactional
    public ReportAdminDto resolve(UUID reportId, UUID adminUserId, String note) {
        return decide(reportId, adminUserId, note, ReportStatus.RESOLVED);
    }

    @Override
    @Transactional
    public ReportAdminDto dismiss(UUID reportId, UUID adminUserId, String note) {
        return decide(reportId, adminUserId, note, ReportStatus.DISMISSED);
    }

    @Override
    @Transactional(readOnly = true)
    public long countPending() {
        return repo.countByStatus(ReportStatus.PENDING);
    }

    private ReportAdminDto decide(UUID reportId, UUID adminUserId, String note, ReportStatus status) {
        Report report = load(reportId);
        report.setStatus(status);
        report.setReviewedAt(timeProvider.now());
        report.setReviewedByUserId(adminUserId);
        report.setResolutionNote(note);
        return ReportAdminDto.from(repo.save(report));
    }

    private Report load(UUID reportId) {
        return repo.findById(reportId)
                .orElseThrow(() -> new AppException(ErrorCode.REPORT_NOT_FOUND));
    }
}
