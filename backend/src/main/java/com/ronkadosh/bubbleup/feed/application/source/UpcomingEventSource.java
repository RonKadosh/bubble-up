package com.ronkadosh.bubbleup.feed.application.source;

import com.ronkadosh.bubbleup.calendar.internal.CalendarInternalService;
import com.ronkadosh.bubbleup.calendar.internal.dto.CalendarEventSummary;
import com.ronkadosh.bubbleup.calendar.model.CalendarOwnerType;
import com.ronkadosh.bubbleup.feed.api.dto.FeedItemResponse;
import com.ronkadosh.bubbleup.feed.application.FeedCtaType;
import com.ronkadosh.bubbleup.feed.application.FeedSection;
import com.ronkadosh.bubbleup.feed.application.spi.FeedContext;
import com.ronkadosh.bubbleup.feed.application.spi.FeedSource;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;

/**
 * UPCOMING: future calendar events across the user's groups, soonest first,
 * within the next two weeks.
 */
@Component
@RequiredArgsConstructor
public class UpcomingEventSource implements FeedSource {

    private static final Duration HORIZON = Duration.ofDays(14);

    private final CalendarInternalService calendarInternalService;

    @Override
    public FeedSection section() {
        return FeedSection.UPCOMING;
    }

    @Override
    public List<FeedItemResponse> fetch(FeedContext ctx) {
        return calendarInternalService.findUpcomingForOwners(
                        CalendarOwnerType.GROUP, ctx.groupIds(), ctx.now(), ctx.now().plus(HORIZON), ctx.fetchLimit())
                .stream()
                .map((CalendarEventSummary e) -> FeedItemResponse.of("upcomingEvent")
                        .group(e.ownerId(), ctx.groupName(e.ownerId()))
                        .title(e.description())
                        .eventType(e.eventType().name())
                        .ts(e.startsAt())
                        .startsAt(e.startsAt())
                        .endsAt(e.endsAt())
                        .cta(FeedCtaType.VIEW_EVENT, e.id())
                        .build())
                .toList();
    }
}
