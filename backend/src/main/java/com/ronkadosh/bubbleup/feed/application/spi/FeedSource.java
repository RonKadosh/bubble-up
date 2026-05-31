package com.ronkadosh.bubbleup.feed.application.spi;

import com.ronkadosh.bubbleup.feed.api.dto.FeedItemResponse;
import com.ronkadosh.bubbleup.feed.application.FeedSection;

import java.util.List;

/**
 * Plugin contract for a single kind of feed content. {@link com.ronkadosh.bubbleup.feed.application.FeedQueryService}
 * auto-collects every {@code FeedSource} bean, so adding a new feed item (e.g. a
 * second LIVE source) is just a new {@code @Component} implementing this interface —
 * no change to the aggregator.
 *
 * <p>A source reads other modules only through their {@code internal/} services,
 * never their repositories. It returns fully-formed {@link FeedItemResponse}s
 * (title/subtitle/cta already resolved); the aggregator only sorts and caps.
 */
public interface FeedSource {

    /** The section this source contributes to. */
    FeedSection section();

    /** Fetch this source's items for the given context. Return up to {@code ctx.fetchLimit()}. */
    List<FeedItemResponse> fetch(FeedContext ctx);
}
