package com.ronkadosh.bubbleup.onboarding.api;

import com.ronkadosh.bubbleup.common.api.ApiPaths;
import com.ronkadosh.bubbleup.common.api.ApiResponse;
import com.ronkadosh.bubbleup.common.context.CurrentUserProvider;
import com.ronkadosh.bubbleup.onboarding.api.dto.AckRequest;
import com.ronkadosh.bubbleup.onboarding.api.dto.CollapsedRequest;
import com.ronkadosh.bubbleup.onboarding.api.dto.LevelRequest;
import com.ronkadosh.bubbleup.onboarding.api.dto.OnboardingStatusResponse;
import com.ronkadosh.bubbleup.onboarding.application.OnboardingCommandService;
import com.ronkadosh.bubbleup.onboarding.application.OnboardingQueryService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping(ApiPaths.ONBOARDING_BASE)
@RequiredArgsConstructor
public class OnboardingController {

    private final OnboardingQueryService queryService;
    private final OnboardingCommandService commandService;
    private final CurrentUserProvider currentUserProvider;

    @GetMapping("/status")
    public ApiResponse<OnboardingStatusResponse> status() {
        UUID userId = currentUserProvider.get().id();
        return ApiResponse.success(queryService.getStatus(userId));
    }

    /** Marks a namespaced key (explainer/guide) as acknowledged so it won't show again. */
    @PostMapping("/ack")
    public ApiResponse<Void> acknowledge(@Valid @RequestBody AckRequest req) {
        commandService.acknowledge(currentUserProvider.get().id(), req.key());
        return ApiResponse.success(null);
    }

    /** Persists whether the user has collapsed the "Getting started" widget. */
    @PutMapping("/collapsed")
    public ApiResponse<Void> collapsed(@Valid @RequestBody CollapsedRequest req) {
        commandService.setCollapsed(currentUserProvider.get().id(), req.collapsed());
        return ApiResponse.success(null);
    }

    /** Persists the onboarding wizard's current level (resumes anywhere). */
    @PutMapping("/level")
    public ApiResponse<Void> level(@Valid @RequestBody LevelRequest req) {
        commandService.setWizardLevel(currentUserProvider.get().id(), req.level());
        return ApiResponse.success(null);
    }
}
