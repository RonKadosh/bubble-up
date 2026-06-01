package com.ronkadosh.bubbleup.feed.application.source;

import com.ronkadosh.bubbleup.feed.api.dto.FeedItemResponse;
import com.ronkadosh.bubbleup.feed.application.FeedCtaType;
import com.ronkadosh.bubbleup.feed.application.FeedSection;
import com.ronkadosh.bubbleup.feed.application.spi.FeedContext;
import com.ronkadosh.bubbleup.feed.application.spi.FeedSource;
import com.ronkadosh.bubbleup.matching.internal.MatchingInternalService;
import com.ronkadosh.bubbleup.matching.internal.dto.GroupRecommendation;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * DISCOVERY: recommended bubbles for the user (matched or trending), from the
 * matching module. Already-joined groups are filtered out. Order is preserved
 * from matching (match score / popularity), so the aggregator does not re-sort.
 */
@Component
@RequiredArgsConstructor
public class DiscoverySource implements FeedSource {

    private final MatchingInternalService matchingInternalService;

    @Override
    public FeedSection section() {
        return FeedSection.DISCOVERY;
    }

    @Override
    public List<FeedItemResponse> fetch(FeedContext ctx) {
        return matchingInternalService.getRecommendations(ctx.userId(), null).stream()
                .filter(r -> !r.alreadyMember())
                .limit(ctx.fetchLimit())
                .map((GroupRecommendation r) -> FeedItemResponse.of("recommendation")
                        .group(r.groupId(), r.groupName())
                        .title(r.groupName())
                        .matchPercent(r.matchPercent())
                        .memberCount(r.memberCount())
                        .displayMode(r.displayMode())
                        .reasonLabels(r.reasonLabels())
                        .cta(FeedCtaType.VIEW_BUBBLE, r.groupId())
                        .build())
                .toList();
    }
}
