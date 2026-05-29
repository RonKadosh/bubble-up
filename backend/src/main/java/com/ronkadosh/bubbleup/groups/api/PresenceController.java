package com.ronkadosh.bubbleup.groups.api;

import com.ronkadosh.bubbleup.common.api.ApiPaths;
import com.ronkadosh.bubbleup.common.api.ApiResponse;
import com.ronkadosh.bubbleup.common.context.CurrentUserProvider;
import com.ronkadosh.bubbleup.groups.api.dto.PresenceResponse;
import com.ronkadosh.bubbleup.groups.application.PresenceQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping(ApiPaths.GROUPS_BASE)
@RequiredArgsConstructor
public class PresenceController {

    private final PresenceQueryService queries;
    private final CurrentUserProvider currentUserProvider;

    @GetMapping("/{id}/presence")
    public ApiResponse<List<PresenceResponse>> getGroupPresence(@PathVariable UUID id) {
        UUID me = currentUserProvider.get().id();
        return ApiResponse.success(queries.snapshotForGroup(id, me));
    }
}
