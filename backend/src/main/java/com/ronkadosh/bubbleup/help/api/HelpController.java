package com.ronkadosh.bubbleup.help.api;

import com.ronkadosh.bubbleup.common.api.ApiPaths;
import com.ronkadosh.bubbleup.common.api.ApiResponse;
import com.ronkadosh.bubbleup.common.ratelimit.RateLimit;
import com.ronkadosh.bubbleup.common.ratelimit.RateLimitScope;
import com.ronkadosh.bubbleup.help.api.dto.HelpAskRequest;
import com.ronkadosh.bubbleup.help.api.dto.HelpAskResponse;
import com.ronkadosh.bubbleup.help.api.dto.HelpQuestionResponse;
import com.ronkadosh.bubbleup.help.api.dto.HelpTopicResponse;
import com.ronkadosh.bubbleup.help.application.HelpQueryService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping(ApiPaths.HELP_BASE)
@RequiredArgsConstructor
public class HelpController {

    private final HelpQueryService helpQueryService;

    @GetMapping("/topics")
    public ApiResponse<List<HelpTopicResponse>> topics(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String currentPath
    ) {
        return ApiResponse.success(helpQueryService.topics(q, currentPath));
    }

    @GetMapping("/questions")
    public ApiResponse<List<HelpQuestionResponse>> questions(
            @RequestParam(required = false) String q,
            @RequestParam(defaultValue = "8") int limit
    ) {
        return ApiResponse.success(helpQueryService.recentQuestions(q, limit));
    }

    @PostMapping("/ask")
    @RateLimit(limit = 12, windowSeconds = 60, scope = RateLimitScope.PER_USER)
    public ApiResponse<HelpAskResponse> ask(@Valid @RequestBody HelpAskRequest request) {
        return ApiResponse.success(helpQueryService.ask(request));
    }
}
