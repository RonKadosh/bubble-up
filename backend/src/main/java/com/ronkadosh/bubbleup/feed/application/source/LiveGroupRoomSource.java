package com.ronkadosh.bubbleup.feed.application.source;

import com.ronkadosh.bubbleup.feed.api.dto.FeedItemResponse;
import com.ronkadosh.bubbleup.feed.application.FeedCtaType;
import com.ronkadosh.bubbleup.feed.application.FeedSection;
import com.ronkadosh.bubbleup.feed.application.spi.FeedContext;
import com.ronkadosh.bubbleup.feed.application.spi.FeedSource;
import com.ronkadosh.bubbleup.room.internal.RoomInternalService;
import com.ronkadosh.bubbleup.room.internal.dto.LiveRoomSummary;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * LIVE: group study rooms that are live now (open join window, not yet closed)
 * among the user's groups. The CTA opens the Bubble Room. Proves the LIVE section
 * is pluggable — a second source alongside {@link LiveExpertSessionSource}.
 */
@Component
@RequiredArgsConstructor
public class LiveGroupRoomSource implements FeedSource {

    private final RoomInternalService roomInternalService;

    @Override
    public FeedSection section() {
        return FeedSection.LIVE;
    }

    @Override
    public List<FeedItemResponse> fetch(FeedContext ctx) {
        return roomInternalService.findLiveGroupRoomsForGroups(ctx.groupIds(), ctx.now()).stream()
                .limit(ctx.fetchLimit())
                .map((LiveRoomSummary r) -> FeedItemResponse.of("liveGroupRoom")
                        .group(r.groupId(), ctx.groupName(r.groupId()))
                        .ts(r.startsAt())
                        .startsAt(r.startsAt())
                        .endsAt(r.endsAt())
                        .participantCount(r.participantCount())
                        .cta(FeedCtaType.JOIN_ROOM, r.roomId())
                        .build())
                .toList();
    }
}
