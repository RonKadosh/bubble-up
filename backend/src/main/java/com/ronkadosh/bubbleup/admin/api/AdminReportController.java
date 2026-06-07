package com.ronkadosh.bubbleup.admin.api;

import com.ronkadosh.bubbleup.admin.api.dto.AdminReportDtos;
import com.ronkadosh.bubbleup.admin.application.AdminReportService;
import com.ronkadosh.bubbleup.common.api.ApiPaths;
import com.ronkadosh.bubbleup.common.api.ApiResponse;
import com.ronkadosh.bubbleup.common.context.CurrentUserProvider;
import com.ronkadosh.bubbleup.common.pagination.PageResponseDto;
import com.ronkadosh.bubbleup.report.internal.dto.ReportImage;
import com.ronkadosh.bubbleup.report.model.ReportStatus;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping(ApiPaths.ADMIN_BASE + "/reports")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class AdminReportController {

    private final AdminReportService service;
    private final CurrentUserProvider currentUserProvider;

    @GetMapping
    public ApiResponse<PageResponseDto<AdminReportDtos.ReportResponse>> list(
            @RequestParam(defaultValue = "PENDING") ReportStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return ApiResponse.success(service.list(status, page, size));
    }

    @GetMapping("/{id}")
    public ApiResponse<AdminReportDtos.ReportResponse> get(@PathVariable UUID id) {
        return ApiResponse.success(service.get(id));
    }

    @GetMapping("/{id}/image")
    public ResponseEntity<byte[]> image(@PathVariable UUID id) {
        ReportImage img = service.image(id);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType(
                img.contentType() == null ? MediaType.APPLICATION_OCTET_STREAM_VALUE : img.contentType()));
        return new ResponseEntity<>(img.bytes(), headers, 200);
    }

    @PostMapping("/{id}/resolve")
    public ApiResponse<AdminReportDtos.ReportResponse> resolve(
            @PathVariable UUID id,
            @Valid @RequestBody AdminReportDtos.DecisionRequest req
    ) {
        return ApiResponse.success(service.resolve(id, req, currentUserProvider.get()));
    }

    @PostMapping("/{id}/dismiss")
    public ApiResponse<AdminReportDtos.ReportResponse> dismiss(
            @PathVariable UUID id,
            @Valid @RequestBody AdminReportDtos.DecisionRequest req
    ) {
        return ApiResponse.success(service.dismiss(id, req, currentUserProvider.get()));
    }
}
