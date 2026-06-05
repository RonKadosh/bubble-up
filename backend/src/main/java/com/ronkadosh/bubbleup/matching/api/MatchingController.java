package com.ronkadosh.bubbleup.matching.api;

import com.ronkadosh.bubbleup.common.api.ApiPaths;
import com.ronkadosh.bubbleup.common.api.ApiResponse;
import com.ronkadosh.bubbleup.common.context.CurrentUserProvider;
import com.ronkadosh.bubbleup.common.ratelimit.RateLimit;
import com.ronkadosh.bubbleup.common.ratelimit.RateLimitScope;
import com.ronkadosh.bubbleup.matching.api.dto.NextQuestionResponse;
import com.ronkadosh.bubbleup.matching.api.dto.RecommendationsResponse;
import com.ronkadosh.bubbleup.matching.api.dto.SubmitAnswerRequest;
import com.ronkadosh.bubbleup.matching.application.MatchingCommandService;
import com.ronkadosh.bubbleup.matching.application.MatchingQueryService;
import com.ronkadosh.bubbleup.matching.internal.dto.MatchingReliability;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Locale;
import java.util.UUID;

@RestController
@RequestMapping(ApiPaths.MATCHING_BASE)
@RequiredArgsConstructor
public class MatchingController {

    private final MatchingCommandService commandService;
    private final MatchingQueryService queryService;
    private final CurrentUserProvider currentUserProvider;

    @GetMapping("/quiz/next")
    public ApiResponse<NextQuestionResponse> nextQuestion(Locale locale) {
        UUID userId = currentUserProvider.get().id();
        return ApiResponse.success(queryService.getNextQuestion(userId, locale.getLanguage()));
    }

    @PostMapping("/quiz/answers")
    @RateLimit(limit = 30, windowSeconds = 60, scope = RateLimitScope.PER_USER)
    public ApiResponse<Void> submitAnswer(@Valid @RequestBody SubmitAnswerRequest req) {
        UUID userId = currentUserProvider.get().id();
        commandService.submitAnswer(userId, req.questionId(), req.answerId());
        return ApiResponse.success(null);
    }

    @GetMapping("/recommendations")
    public ApiResponse<RecommendationsResponse> recommendations(
            @RequestParam(required = false) UUID courseId) {
        UUID userId = currentUserProvider.get().id();
        return ApiResponse.success(queryService.getRecommendations(userId, courseId));
    }

    /** The caller's own private profile strength (matching reliability) — Settings + onboarding. */
    @GetMapping("/reliability")
    public ApiResponse<MatchingReliability> reliability() {
        UUID userId = currentUserProvider.get().id();
        return ApiResponse.success(queryService.getReliability(userId));
    }
}
