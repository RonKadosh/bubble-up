package com.ronkadosh.bubbleup.feed.application;

/**
 * The feed's sections, in render/priority order (declaration order). Each
 * {@link com.ronkadosh.bubbleup.feed.application.spi.FeedSource} contributes to
 * exactly one section; {@link FeedQueryService} groups, sorts, and caps per section.
 */
public enum FeedSection {
    LIVE,
    UPCOMING,
    ACTIVITY,
    DISCOVERY
}
