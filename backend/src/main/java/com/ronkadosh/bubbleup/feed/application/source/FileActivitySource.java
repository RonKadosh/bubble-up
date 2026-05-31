package com.ronkadosh.bubbleup.feed.application.source;

import com.ronkadosh.bubbleup.feed.api.dto.FeedItemResponse;
import com.ronkadosh.bubbleup.feed.application.FeedCtaType;
import com.ronkadosh.bubbleup.feed.application.FeedSection;
import com.ronkadosh.bubbleup.feed.application.spi.FeedContext;
import com.ronkadosh.bubbleup.feed.application.spi.FeedSource;
import com.ronkadosh.bubbleup.groups.internal.GroupInternalService;
import com.ronkadosh.bubbleup.groups.internal.dto.GroupFileActivityItem;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * ACTIVITY: recently uploaded group files ("lecture3.pdf uploaded to Calc Bubble").
 */
@Component
@RequiredArgsConstructor
public class FileActivitySource implements FeedSource {

    private final GroupInternalService groupInternalService;

    @Override
    public FeedSection section() {
        return FeedSection.ACTIVITY;
    }

    @Override
    public List<FeedItemResponse> fetch(FeedContext ctx) {
        return groupInternalService.findRecentFilesForGroups(ctx.groupIds(), ctx.fetchLimit()).stream()
                .map((GroupFileActivityItem f) -> FeedItemResponse.of("file")
                        .group(f.groupId(), ctx.groupName(f.groupId()))
                        .title(f.originalName())
                        .ts(f.uploadedAt())
                        .cta(FeedCtaType.VIEW_BUBBLE, f.groupId())
                        .build())
                .toList();
    }
}
