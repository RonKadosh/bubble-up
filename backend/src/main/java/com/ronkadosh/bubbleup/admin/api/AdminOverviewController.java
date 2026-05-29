package com.ronkadosh.bubbleup.admin.api;

import com.ronkadosh.bubbleup.admin.api.dto.AdminOverviewDtos;
import com.ronkadosh.bubbleup.admin.application.AdminOverviewService;
import com.ronkadosh.bubbleup.common.api.ApiPaths;
import com.ronkadosh.bubbleup.common.api.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(ApiPaths.ADMIN_BASE + "/overview")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class AdminOverviewController {

    private final AdminOverviewService service;

    @GetMapping
    public ApiResponse<AdminOverviewDtos.Overview> get() {
        return ApiResponse.success(service.build());
    }
}
