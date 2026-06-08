package com.ronkadosh.bubbleup.matchingfeedback.api;

import com.ronkadosh.bubbleup.common.api.ApiPaths;
import com.ronkadosh.bubbleup.common.api.ApiResponse;
import com.ronkadosh.bubbleup.matchingfeedback.api.dto.MatchFeedbackAnalytics;
import com.ronkadosh.bubbleup.matchingfeedback.application.MatchFeedbackService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Admin reflection surface: the matching-feedback funnel + match-% buckets + rating totals. */
@RestController
@RequestMapping(ApiPaths.ADMIN_MATCHING_FEEDBACK_BASE)
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class AdminMatchFeedbackController {

    private final MatchFeedbackService service;

    @GetMapping
    public ApiResponse<MatchFeedbackAnalytics> get() {
        return ApiResponse.success(service.buildAnalytics());
    }
}
