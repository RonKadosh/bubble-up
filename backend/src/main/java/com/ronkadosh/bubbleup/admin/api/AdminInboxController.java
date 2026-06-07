package com.ronkadosh.bubbleup.admin.api;

import com.ronkadosh.bubbleup.admin.application.AdminInboxService;
import com.ronkadosh.bubbleup.common.api.ApiPaths;
import com.ronkadosh.bubbleup.common.api.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(ApiPaths.ADMIN_BASE + "/inbox")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class AdminInboxController {

    private final AdminInboxService service;

    @GetMapping("/counts")
    public ApiResponse<AdminInboxService.InboxCounts> counts() {
        return ApiResponse.success(service.counts());
    }
}
