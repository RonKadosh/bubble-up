package com.ronkadosh.bubbleup.matchingfeedback.api;

import com.ronkadosh.bubbleup.common.api.ApiPaths;
import com.ronkadosh.bubbleup.common.api.ApiResponse;
import com.ronkadosh.bubbleup.common.context.CurrentUserProvider;
import com.ronkadosh.bubbleup.matchingfeedback.api.dto.FeedbackStatusResponse;
import com.ronkadosh.bubbleup.matchingfeedback.api.dto.SubmitFeedbackRequest;
import com.ronkadosh.bubbleup.matchingfeedback.application.MatchFeedbackService;
import com.ronkadosh.bubbleup.matchingfeedback.model.FeedbackRating;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/** The caller rates a bubble's fit from the group view. The match % is resolved server-side. */
@RestController
@RequestMapping(ApiPaths.MATCHING_FEEDBACK_BASE)
@RequiredArgsConstructor
public class MatchFeedbackController {

    private final MatchFeedbackService service;
    private final CurrentUserProvider currentUserProvider;

    @PostMapping
    public ApiResponse<Void> submit(@Valid @RequestBody SubmitFeedbackRequest req) {
        UUID userId = currentUserProvider.get().id();
        service.recordRating(userId, req.groupId(), req.sentiment());
        return ApiResponse.success(null);
    }

    /** Whether the caller has already rated this bubble — the UI uses it to ask only once. */
    @GetMapping("/{groupId}")
    public ApiResponse<FeedbackStatusResponse> status(@PathVariable UUID groupId) {
        UUID userId = currentUserProvider.get().id();
        FeedbackRating existing = service.getRating(userId, groupId).orElse(null);
        return ApiResponse.success(new FeedbackStatusResponse(existing != null, existing));
    }
}
