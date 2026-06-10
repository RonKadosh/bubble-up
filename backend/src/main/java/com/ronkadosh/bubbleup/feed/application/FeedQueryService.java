package com.ronkadosh.bubbleup.feed.application;

import com.ronkadosh.bubbleup.common.datetime.TimeProvider;
import com.ronkadosh.bubbleup.feed.api.dto.FeedItemResponse;
import com.ronkadosh.bubbleup.feed.api.dto.FeedResponse;
import com.ronkadosh.bubbleup.feed.api.dto.FeedSectionResponse;
import com.ronkadosh.bubbleup.feed.application.spi.FeedContext;
import com.ronkadosh.bubbleup.feed.application.spi.FeedSource;
import com.ronkadosh.bubbleup.groups.internal.GroupInternalService;
import com.ronkadosh.bubbleup.groups.internal.dto.GroupSummary;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Aggregates the dashboard feed. Auto-collects every {@link FeedSource} bean (Spring
 * injects the {@code List}), groups their output by section, sorts + caps each, and
 * emits non-empty sections in priority order. Adding a feed item is a new
 * {@code FeedSource} bean — this class does not change.
 */
@Service
@RequiredArgsConstructor
public class FeedQueryService {

    /** How many items each source is asked to fetch (≥ the largest section cap). */
    private static final int FETCH_LIMIT = 6;

    /** Per-section display caps after merge. */
    private static final Map<FeedSection, Integer> SECTION_CAPS = Map.of(
            FeedSection.LIVE, 4,
            FeedSection.UPCOMING, 4,
            FeedSection.ACTIVITY, 6,
            FeedSection.DISCOVERY, 3
    );

    private final List<FeedSource> sources;
    private final GroupInternalService groupInternalService;
    private final TimeProvider timeProvider;

    public FeedResponse getFeed(UUID userId) {
        List<GroupSummary> groups = groupInternalService.getGroupsForUser(userId);
        Set<UUID> groupIds = new LinkedHashSet<>();
        Map<UUID, String> groupNames = new LinkedHashMap<>();
        for (GroupSummary g : groups) {
            groupIds.add(g.id());
            groupNames.put(g.id(), g.name());
        }

        FeedContext ctx = new FeedContext(userId, groupIds, groupNames, timeProvider.now(), FETCH_LIMIT);

        Map<FeedSection, List<FeedItemResponse>> bySection = new EnumMap<>(FeedSection.class);
        Set<UUID> itemGroupIds = new LinkedHashSet<>();
        for (FeedSource source : sources) {
            List<FeedItemResponse> items = source.fetch(ctx);
            if (items.isEmpty()) continue;
            for (FeedItemResponse i : items) if (i.groupId() != null) itemGroupIds.add(i.groupId());
            bySection.computeIfAbsent(source.section(), s -> new ArrayList<>()).addAll(items);
        }

        // Cover-image URLs for every group referenced by any item — including
        // discovery (non-member) groups, so a Bubble's photo shows on its cards.
        Map<UUID, String> imageUrls = groupInternalService.getGroupImageUrls(itemGroupIds);

        List<FeedSectionResponse> sections = new ArrayList<>();
        for (FeedSection section : FeedSection.values()) {
            List<FeedItemResponse> items = bySection.get(section);
            if (items == null || items.isEmpty()) continue;
            sortSection(section, items);
            int cap = SECTION_CAPS.getOrDefault(section, FETCH_LIMIT);
            List<FeedItemResponse> capped = items.size() > cap ? items.subList(0, cap) : items;
            List<FeedItemResponse> withImages = capped.stream()
                    .map(i -> i.groupId() != null && imageUrls.containsKey(i.groupId())
                            ? i.withGroupImage(imageUrls.get(i.groupId()))
                            : i)
                    .toList();
            sections.add(new FeedSectionResponse(section.name(), withImages));
        }
        return new FeedResponse(sections);
    }

    private void sortSection(FeedSection section, List<FeedItemResponse> items) {
        switch (section) {
            // Forward-looking: soonest first.
            case LIVE, UPCOMING -> items.sort(Comparator.comparing(
                    FeedItemResponse::startsAt, Comparator.nullsLast(Comparator.naturalOrder())));
            // Backward-looking: newest first.
            case ACTIVITY -> items.sort(Comparator.comparing(
                    FeedItemResponse::ts, Comparator.nullsLast(Comparator.reverseOrder())));
            // Discovery keeps the matching module's order (match score / popularity).
            case DISCOVERY -> { /* no-op */ }
        }
    }
}
