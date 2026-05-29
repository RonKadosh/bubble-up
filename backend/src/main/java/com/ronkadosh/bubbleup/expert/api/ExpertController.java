package com.ronkadosh.bubbleup.expert.api;

import com.ronkadosh.bubbleup.common.api.ApiPaths;
import com.ronkadosh.bubbleup.common.api.ApiResponse;
import com.ronkadosh.bubbleup.common.context.CurrentUser;
import com.ronkadosh.bubbleup.common.context.CurrentUserProvider;
import com.ronkadosh.bubbleup.expert.api.dto.ApplyAsExpertRequest;
import com.ronkadosh.bubbleup.expert.api.dto.ExpertProfileResponse;
import com.ronkadosh.bubbleup.expert.api.dto.UpdateExpertProfileRequest;
import com.ronkadosh.bubbleup.expert.application.ExpertProfileService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping(ApiPaths.EXPERTS_BASE)
@RequiredArgsConstructor
public class ExpertController {

    private final ExpertProfileService profiles;
    private final CurrentUserProvider currentUserProvider;

    @PostMapping("/apply")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<ExpertProfileResponse> apply(@Valid @RequestBody ApplyAsExpertRequest request) {
        CurrentUser me = currentUserProvider.get();
        return ApiResponse.success(profiles.applyAsExpert(me.id(), request));
    }

    @GetMapping("/me")
    public ApiResponse<ExpertProfileResponse> getMyProfile() {
        CurrentUser me = currentUserProvider.get();
        return ApiResponse.success(profiles.getMyProfile(me.id()));
    }

    @PatchMapping("/me")
    public ApiResponse<ExpertProfileResponse> updateMyProfile(
            @Valid @RequestBody UpdateExpertProfileRequest request) {
        CurrentUser me = currentUserProvider.get();
        return ApiResponse.success(profiles.updateMyProfile(me.id(), request));
    }

    @GetMapping("/directory")
    public ApiResponse<List<ExpertProfileResponse>> listDirectory() {
        return ApiResponse.success(profiles.listVerifiedDirectory());
    }

    @GetMapping("/{userId}")
    public ApiResponse<ExpertProfileResponse> getPublicProfile(@PathVariable UUID userId) {
        return ApiResponse.success(profiles.getPublicProfile(userId));
    }
}
