package com.ronkadosh.bubbleup.feed.api.dto;

import java.util.List;

/**
 * The dashboard feed: an ordered list of non-empty sections (LIVE first, then
 * UPCOMING, ACTIVITY, DISCOVERY). A bounded digest — no pagination.
 */
public record FeedResponse(List<FeedSectionResponse> sections) {}
