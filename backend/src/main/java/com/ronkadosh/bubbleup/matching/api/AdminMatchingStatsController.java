package com.ronkadosh.bubbleup.matching.api;

import com.ronkadosh.bubbleup.common.api.ApiPaths;
import com.ronkadosh.bubbleup.common.api.ApiResponse;
import com.ronkadosh.bubbleup.matching.api.dto.MatchingStats;
import com.ronkadosh.bubbleup.matching.application.MatchingStatsService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Admin-only aggregate stats about the character-profile population (no individual data). */
@RestController
@RequestMapping(ApiPaths.ADMIN_MATCHING_STATS_BASE)
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class AdminMatchingStatsController {

    private final MatchingStatsService service;

    @GetMapping
    public ApiResponse<MatchingStats> get() {
        return ApiResponse.success(service.build());
    }
}
