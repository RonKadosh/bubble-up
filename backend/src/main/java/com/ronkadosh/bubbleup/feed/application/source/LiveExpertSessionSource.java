package com.ronkadosh.bubbleup.feed.application.source;

import com.ronkadosh.bubbleup.expert.internal.ExpertInternalService;
import com.ronkadosh.bubbleup.expert.internal.dto.ExpertSessionSummary;
import com.ronkadosh.bubbleup.feed.api.dto.FeedItemResponse;
import com.ronkadosh.bubbleup.feed.application.FeedCtaType;
import com.ronkadosh.bubbleup.feed.application.FeedSection;
import com.ronkadosh.bubbleup.feed.application.spi.FeedContext;
import com.ronkadosh.bubbleup.feed.application.spi.FeedSource;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * LIVE: expert sessions one of the user's groups is enrolled in that are joinable
 * now — i.e. starting within the next 15 minutes or already running. Reuses
 * {@link ExpertInternalService#findActiveSessionsOverlappingForGroup} with a
 * {@code [now, now+15m)} window, which matches the room's join gate.
 */
@Component
@RequiredArgsConstructor
public class LiveExpertSessionSource implements FeedSource {

    /** Mirrors the expert room join window (startsAt - 15min). */
    private static final Duration JOIN_WINDOW = Duration.ofMinutes(15);

    private final ExpertInternalService expertInternalService;

    @Override
    public FeedSection section() {
        return FeedSection.LIVE;
    }

    @Override
    public List<FeedItemResponse> fetch(FeedContext ctx) {
        List<FeedItemResponse> out = new ArrayList<>();
        Set<UUID> seenSessions = new HashSet<>();
        for (UUID groupId : ctx.groupIds()) {
            List<ExpertSessionSummary> sessions = expertInternalService
                    .findActiveSessionsOverlappingForGroup(groupId, ctx.now(), ctx.now().plus(JOIN_WINDOW));
            for (ExpertSessionSummary s : sessions) {
                if (!seenSessions.add(s.id())) continue;   // a session shared by two of my groups shows once
                out.add(FeedItemResponse.of("liveSession")
                        .group(groupId, ctx.groupName(groupId))
                        .title(s.title())
                        .ts(s.startsAt())
                        .startsAt(s.startsAt())
                        .endsAt(s.endsAt())
                        .cta(FeedCtaType.JOIN_SESSION, s.id())
                        .build());
                if (out.size() >= ctx.fetchLimit()) return out;
            }
        }
        return out;
    }
}
