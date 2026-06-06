package com.ronkadosh.bubbleup.groups.api;

import com.ronkadosh.bubbleup.common.api.ApiPaths;
import com.ronkadosh.bubbleup.common.api.ApiResponse;
import com.ronkadosh.bubbleup.common.context.CurrentUserProvider;
import com.ronkadosh.bubbleup.common.ratelimit.RateLimit;
import com.ronkadosh.bubbleup.common.ratelimit.RateLimitScope;
import com.ronkadosh.bubbleup.groups.api.dto.CreateFolderRequest;
import com.ronkadosh.bubbleup.groups.api.dto.GroupFolderResponse;
import com.ronkadosh.bubbleup.groups.application.GroupFolderCommandService;
import com.ronkadosh.bubbleup.groups.application.GroupFolderQueryService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping(ApiPaths.GROUPS_BASE + "/{groupId}/folders")
@RequiredArgsConstructor
public class GroupFolderController {

    private final GroupFolderCommandService commands;
    private final GroupFolderQueryService queries;
    private final CurrentUserProvider currentUserProvider;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @RateLimit(limit = 20, windowSeconds = 60, scope = RateLimitScope.PER_USER_PER_GROUP)
    public ApiResponse<GroupFolderResponse> create(
            @PathVariable UUID groupId,
            @Valid @RequestBody CreateFolderRequest req
    ) {
        UUID me = currentUserProvider.get().id();
        return ApiResponse.success(commands.create(groupId, me, req.name(), req.parentId()));
    }

    @GetMapping
    public ApiResponse<List<GroupFolderResponse>> list(@PathVariable UUID groupId) {
        UUID me = currentUserProvider.get().id();
        return ApiResponse.success(queries.list(groupId, me));
    }

    @DeleteMapping("/{folderId}")
    public ApiResponse<Void> delete(
            @PathVariable UUID groupId,
            @PathVariable UUID folderId
    ) {
        UUID me = currentUserProvider.get().id();
        commands.delete(groupId, folderId, me);
        return ApiResponse.ok();
    }
}
