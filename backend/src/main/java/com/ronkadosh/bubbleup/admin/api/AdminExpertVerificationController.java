package com.ronkadosh.bubbleup.admin.api;

import com.ronkadosh.bubbleup.admin.api.dto.AdminExpertDtos;
import com.ronkadosh.bubbleup.admin.application.AdminExpertVerificationService;
import com.ronkadosh.bubbleup.common.api.ApiPaths;
import com.ronkadosh.bubbleup.common.api.ApiResponse;
import com.ronkadosh.bubbleup.common.context.CurrentUserProvider;
import com.ronkadosh.bubbleup.expert.model.VerificationStatus;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping(ApiPaths.ADMIN_BASE + "/experts")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class AdminExpertVerificationController {

    private final AdminExpertVerificationService service;
    private final CurrentUserProvider currentUserProvider;

    @GetMapping
    public ApiResponse<List<AdminExpertDtos.ExpertResponse>> list(
            @RequestParam(defaultValue = "PENDING") VerificationStatus status
    ) {
        return ApiResponse.success(service.listByStatus(status));
    }

    @GetMapping("/{userId}")
    public ApiResponse<AdminExpertDtos.ExpertResponse> get(@PathVariable UUID userId) {
        return ApiResponse.success(service.getByUser(userId));
    }

    @PostMapping("/{userId}/verify")
    public ApiResponse<AdminExpertDtos.ExpertResponse> verify(
            @PathVariable UUID userId,
            @Valid @RequestBody AdminExpertDtos.VerifyRequest req
    ) {
        return ApiResponse.success(service.verify(userId, req, currentUserProvider.get()));
    }

    @PostMapping("/{userId}/reject")
    public ApiResponse<Void> reject(
            @PathVariable UUID userId,
            @Valid @RequestBody AdminExpertDtos.RejectRequest req
    ) {
        service.reject(userId, req, currentUserProvider.get());
        return ApiResponse.success(null);
    }

    @PostMapping("/{userId}/revoke")
    public ApiResponse<AdminExpertDtos.ExpertResponse> revoke(
            @PathVariable UUID userId,
            @Valid @RequestBody AdminExpertDtos.RevokeRequest req
    ) {
        return ApiResponse.success(service.revoke(userId, req, currentUserProvider.get()));
    }
}
