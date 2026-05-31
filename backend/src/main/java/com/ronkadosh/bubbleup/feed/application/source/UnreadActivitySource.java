package com.ronkadosh.bubbleup.feed.application.source;

import com.ronkadosh.bubbleup.chat.internal.ChatInternalService;
import com.ronkadosh.bubbleup.chat.internal.dto.GroupUnreadSummary;
import com.ronkadosh.bubbleup.feed.api.dto.FeedItemResponse;
import com.ronkadosh.bubbleup.feed.application.FeedCtaType;
import com.ronkadosh.bubbleup.feed.application.FeedSection;
import com.ronkadosh.bubbleup.feed.application.spi.FeedContext;
import com.ronkadosh.bubbleup.feed.application.spi.FeedSource;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * ACTIVITY: per-group unread-message rollup. Rather than show message text, the
 * feed surfaces a "you have N unread messages in &lt;bubble&gt;" card that links
 * back into the bubble.
 */
@Component
@RequiredArgsConstructor
public class UnreadActivitySource implements FeedSource {

    private final ChatInternalService chatInternalService;

    @Override
    public FeedSection section() {
        return FeedSection.ACTIVITY;
    }

    @Override
    public List<FeedItemResponse> fetch(FeedContext ctx) {
        return chatInternalService.getUnreadSummaryForGroups(ctx.userId(), ctx.groupIds()).stream()
                .map((GroupUnreadSummary u) -> FeedItemResponse.of("unread")
                        .group(u.groupId(), ctx.groupName(u.groupId()))
                        .ts(u.latestMessageAt() != null ? u.latestMessageAt() : ctx.now())
                        .unreadCount(u.unreadCount())
                        .cta(FeedCtaType.VIEW_BUBBLE, u.groupId())
                        .build())
                .toList();
    }
}
