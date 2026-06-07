package com.ronkadosh.bubbleup.report.application;

import com.ronkadosh.bubbleup.common.error.AppException;
import com.ronkadosh.bubbleup.common.error.ErrorCode;
import com.ronkadosh.bubbleup.common.file.FileAccessPolicy;
import com.ronkadosh.bubbleup.common.file.FileStorageService;
import com.ronkadosh.bubbleup.common.file.FileUploadRequest;
import com.ronkadosh.bubbleup.common.file.StoredFile;
import com.ronkadosh.bubbleup.report.api.dto.CreateReportRequest;
import com.ronkadosh.bubbleup.report.api.dto.ReportResponse;
import com.ronkadosh.bubbleup.report.model.Report;
import com.ronkadosh.bubbleup.report.model.ReportStatus;
import com.ronkadosh.bubbleup.report.persistence.ReportRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/**
 * Submission side of the Report Center. The optional image rides a separate
 * single-file endpoint (mirroring the avatar upload) so the create endpoint
 * stays a pure JSON request-DTO handler.
 */
@Service
@Slf4j
public class ReportCommandService {

    private final ReportRepository repo;
    private final FileStorageService fileStorageService;
    private final TransactionTemplate transactionTemplate;

    public ReportCommandService(ReportRepository repo,
                                FileStorageService fileStorageService,
                                PlatformTransactionManager transactionManager) {
        this.repo = repo;
        this.fileStorageService = fileStorageService;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    /** Same whitelist + cap as avatars — reports only accept raster screenshots. */
    private static final Set<String> ALLOWED_IMAGE_MIME = Set.of(
            "image/png", "image/jpeg", "image/webp", "image/gif");
    private static final long MAX_IMAGE_BYTES = 5L * 1024L * 1024L;

    @Transactional
    public ReportResponse submit(UUID reporterUserId, CreateReportRequest request) {
        Report saved = repo.save(Report.builder()
                .reporterUserId(reporterUserId)
                .category(request.category())
                .subject(request.subject().trim())
                .description(request.description().trim())
                .status(ReportStatus.PENDING)
                .build());
        return ReportResponse.from(saved);
    }

    /**
     * Attaches (or replaces) the report's image. Uploads outside the DB write so
     * a failure leaves no orphan row; best-effort deletes any prior attachment.
     */
    public ReportResponse attachImage(UUID reportId, UUID reporterUserId,
                                      byte[] bytes, String contentType, String originalName) {
        requireAllowedImage(contentType, bytes == null ? 0L : bytes.length);
        StoredFile uploaded = fileStorageService.upload(new FileUploadRequest(
                originalName == null ? "report-image" : originalName,
                contentType,
                bytes,
                FileAccessPolicy.PRIVATE));
        StampResult result;
        try {
            result = transactionTemplate.execute(status ->
                    stamp(reportId, reporterUserId, uploaded.fileId(), contentType));
        } catch (RuntimeException ex) {
            bestEffortDelete(uploaded.fileId());
            throw ex;
        }
        if (result != null && result.oldFileId() != null) bestEffortDelete(result.oldFileId());
        return result == null ? null : result.response();
    }

    private StampResult stamp(UUID reportId, UUID reporterUserId, String fileId, String contentType) {
        Report report = repo.findById(reportId)
                .orElseThrow(() -> new AppException(ErrorCode.REPORT_NOT_FOUND));
        if (!report.getReporterUserId().equals(reporterUserId)) {
            throw new AppException(ErrorCode.NOT_REPORT_OWNER);
        }
        String oldFileId = report.getAttachmentFileId();
        report.setAttachmentFileId(fileId);
        report.setAttachmentContentType(contentType);
        return new StampResult(ReportResponse.from(repo.save(report)), oldFileId);
    }

    private void requireAllowedImage(String contentType, long sizeBytes) {
        if (contentType == null || !ALLOWED_IMAGE_MIME.contains(contentType.toLowerCase(Locale.ROOT))) {
            throw new AppException(ErrorCode.REPORT_IMAGE_TYPE_NOT_ALLOWED);
        }
        if (sizeBytes > MAX_IMAGE_BYTES) {
            throw new AppException(ErrorCode.REPORT_IMAGE_TOO_LARGE);
        }
    }

    private void bestEffortDelete(String fileId) {
        try {
            fileStorageService.delete(fileId);
        } catch (Exception e) {
            log.warn("Best-effort delete of report image {} failed: {}", fileId, e.getMessage());
        }
    }

    private record StampResult(ReportResponse response, String oldFileId) {}
}
