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

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * ACTIVITY: recently uploaded group files collapsed to one card per Bubble
 * ("lecture3.pdf and 4 more uploaded to Calc Bubble"). Reads a wide window so the
 * rollup count survives the section cap, then emits one item per group with the
 * newest upload as the sort key. The count reflects the read window, not an
 * all-time total (matches the prior "most recent N files" behaviour).
 */
@Component
@RequiredArgsConstructor
public class FileActivitySource implements FeedSource {

    /** Read this many recent files (independent of the per-section cap) so the
     *  collapsed count reflects a real burst, not just what fits on screen. */
    private static final int READ_LIMIT = 30;

    /** Lead file names shown before the "+N more" overflow. */
    private static final int LEAD_LABELS = 3;

    private final GroupInternalService groupInternalService;

    @Override
    public FeedSection section() {
        return FeedSection.ACTIVITY;
    }

    @Override
    public List<FeedItemResponse> fetch(FeedContext ctx) {
        List<GroupFileActivityItem> files =
                groupInternalService.findRecentFilesForGroups(ctx.groupIds(), READ_LIMIT);
        if (files.isEmpty()) return List.of();

        // Group by group, preserving newest-first order from the query.
        Map<UUID, Bucket> buckets = new LinkedHashMap<>();
        for (GroupFileActivityItem f : files) {
            Bucket b = buckets.computeIfAbsent(f.groupId(), g -> new Bucket(f.uploadedAt()));
            if (f.uploadedAt() != null && (b.newest == null || f.uploadedAt().isAfter(b.newest))) {
                b.newest = f.uploadedAt();
            }
            b.count++;
            if (b.names.size() < LEAD_LABELS) b.names.add(f.originalName());
        }

        List<FeedItemResponse> out = new ArrayList<>(buckets.size());
        buckets.forEach((groupId, b) -> out.add(FeedItemResponse.of("file")
                .group(groupId, ctx.groupName(groupId))
                .collapsed(b.names, b.count)
                .ts(b.newest)
                .cta(FeedCtaType.VIEW_BUBBLE, groupId)
                .build()));
        return out;
    }

    /** Mutable per-group accumulator: lead file names + total count + newest upload. */
    private static final class Bucket {
        final List<String> names = new ArrayList<>();
        int count;
        Instant newest;
        Bucket(Instant newest) { this.newest = newest; }
    }
}
