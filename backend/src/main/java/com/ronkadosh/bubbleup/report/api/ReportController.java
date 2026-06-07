package com.ronkadosh.bubbleup.report.api;

import com.ronkadosh.bubbleup.common.api.ApiPaths;
import com.ronkadosh.bubbleup.common.api.ApiResponse;
import com.ronkadosh.bubbleup.common.context.CurrentUser;
import com.ronkadosh.bubbleup.common.context.CurrentUserProvider;
import com.ronkadosh.bubbleup.common.error.AppException;
import com.ronkadosh.bubbleup.common.error.ErrorCode;
import com.ronkadosh.bubbleup.common.ratelimit.RateLimit;
import com.ronkadosh.bubbleup.common.ratelimit.RateLimitScope;
import com.ronkadosh.bubbleup.report.api.dto.CreateReportRequest;
import com.ronkadosh.bubbleup.report.api.dto.ReportResponse;
import com.ronkadosh.bubbleup.report.application.ReportCommandService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.UUID;

@RestController
@RequestMapping(ApiPaths.REPORTS_BASE)
@RequiredArgsConstructor
public class ReportController {

    private final ReportCommandService commands;
    private final CurrentUserProvider currentUserProvider;

    /** File a report. The optional image is attached via the follow-up endpoint below. */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @RateLimit(limit = 10, windowSeconds = 3600, scope = RateLimitScope.PER_USER)
    public ApiResponse<ReportResponse> submit(@Valid @RequestBody CreateReportRequest request) {
        CurrentUser me = currentUserProvider.get();
        return ApiResponse.success(commands.submit(me.id(), request));
    }

    /**
     * Attach a screenshot to a report you own. Single-file multipart, mirroring
     * the avatar upload at {@code POST /api/users/me/avatar}.
     */
    @PostMapping("/{id}/image")
    @RateLimit(limit = 10, windowSeconds = 3600, scope = RateLimitScope.PER_USER)
    public ApiResponse<ReportResponse> attachImage(
            @PathVariable UUID id,
            @RequestParam("file") MultipartFile file
    ) {
        CurrentUser me = currentUserProvider.get();
        if (file == null || file.isEmpty()) {
            throw new AppException(ErrorCode.REPORT_IMAGE_TYPE_NOT_ALLOWED);
        }
        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (IOException e) {
            throw new AppException(ErrorCode.REPORT_IMAGE_TYPE_NOT_ALLOWED, "Could not read report image upload");
        }
        return ApiResponse.success(
                commands.attachImage(id, me.id(), bytes, file.getContentType(), file.getOriginalFilename()));
    }
}
