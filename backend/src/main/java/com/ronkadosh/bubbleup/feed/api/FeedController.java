package com.ronkadosh.bubbleup.feed.api;

import com.ronkadosh.bubbleup.common.api.ApiPaths;
import com.ronkadosh.bubbleup.common.api.ApiResponse;
import com.ronkadosh.bubbleup.common.context.CurrentUserProvider;
import com.ronkadosh.bubbleup.feed.api.dto.FeedResponse;
import com.ronkadosh.bubbleup.feed.application.FeedQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * The dashboard feed: one curated, sectioned digest (Live / Upcoming / Bubble
 * activity / Discovery). Bounded — no pagination. Replaces the old client-side
 * fan-out of ~26 requests.
 */
@RestController
@RequestMapping(ApiPaths.FEED_BASE)
@RequiredArgsConstructor
public class FeedController {

    private final FeedQueryService feedQueryService;
    private final CurrentUserProvider currentUserProvider;

    @GetMapping
    public ApiResponse<FeedResponse> getFeed() {
        UUID me = currentUserProvider.get().id();
        return ApiResponse.success(feedQueryService.getFeed(me));
    }
}
