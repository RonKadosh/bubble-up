package com.ronkadosh.bubbleup.feed.api.dto;

import java.util.List;

/**
 * One section of the dashboard feed. {@code key} is the section name
 * ({@code LIVE | UPCOMING | ACTIVITY | DISCOVERY}); the frontend renders a header
 * per key. Empty sections are omitted from {@link FeedResponse}.
 */
public record FeedSectionResponse(String key, List<FeedItemResponse> items) {}
