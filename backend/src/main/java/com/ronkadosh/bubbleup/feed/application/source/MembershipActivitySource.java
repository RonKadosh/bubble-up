package com.ronkadosh.bubbleup.feed.application.source;

import com.ronkadosh.bubbleup.auth.internal.AuthInternalService;
import com.ronkadosh.bubbleup.auth.internal.dto.UserIdentity;
import com.ronkadosh.bubbleup.chat.internal.ChatInternalService;
import com.ronkadosh.bubbleup.chat.internal.dto.MembershipEventItem;
import com.ronkadosh.bubbleup.chat.model.ChatMessageType;
import com.ronkadosh.bubbleup.feed.api.dto.FeedItemResponse;
import com.ronkadosh.bubbleup.feed.application.FeedCtaType;
import com.ronkadosh.bubbleup.feed.application.FeedSection;
import com.ronkadosh.bubbleup.feed.application.spi.FeedContext;
import com.ronkadosh.bubbleup.feed.application.spi.FeedSource;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * ACTIVITY: recent membership signals collapsed to one card per Bubble per kind
 * ("Maya, Jon and 3 others floated into Algorithms Bubble") derived from
 * SYSTEM_JOIN / SYSTEM_LEAVE chat messages. Reads a wide window so the rollup
 * count is accurate even when a join wave exceeds the section cap, then emits one
 * item per (group, join/leave). Actor display names are resolved in one batch.
 */
@Component
@RequiredArgsConstructor
public class MembershipActivitySource implements FeedSource {

    /** Read this many recent events (independent of the per-section cap) so the
     *  collapsed count reflects a real wave, not just what fits on screen. */
    private static final int READ_LIMIT = 30;

    /** Lead names shown before the "+N others" overflow. */
    private static final int LEAD_LABELS = 3;

    private final ChatInternalService chatInternalService;
    private final AuthInternalService authInternalService;

    @Override
    public FeedSection section() {
        return FeedSection.ACTIVITY;
    }

    @Override
    public List<FeedItemResponse> fetch(FeedContext ctx) {
        List<MembershipEventItem> events =
                chatInternalService.findRecentMembershipEventsForGroups(ctx.groupIds(), READ_LIMIT);
        if (events.isEmpty()) return List.of();

        Set<UUID> actorIds = new HashSet<>();
        for (MembershipEventItem e : events) {
            if (e.subjectUserId() != null) actorIds.add(e.subjectUserId());
        }
        Map<UUID, UserIdentity> identities = actorIds.isEmpty()
                ? Map.of()
                : authInternalService.getIdentitiesByIds(actorIds);

        // Group by (group, kind), preserving newest-first order from the query. Within a
        // bucket, dedup by actor (someone re-joining counts once) and keep the newest ts.
        Map<String, Bucket> buckets = new LinkedHashMap<>();
        for (MembershipEventItem e : events) {
            String kind = e.messageType() == ChatMessageType.SYSTEM_JOIN ? "memberJoin" : "memberLeave";
            Bucket b = buckets.computeIfAbsent(e.groupId() + "|" + kind,
                    k -> new Bucket(e.groupId(), kind, e.sentAt()));
            if (e.sentAt() != null && (b.newest == null || e.sentAt().isAfter(b.newest))) b.newest = e.sentAt();
            // Key by user id (or a synthetic per-event key for nameless actors) so distinct
            // anonymous events still count toward the total, while a repeat actor collapses to one.
            String dedupKey = e.subjectUserId() != null ? e.subjectUserId().toString() : "anon:" + b.count;
            if (!b.seen.add(dedupKey)) continue;
            b.count++;
            UserIdentity actor = e.subjectUserId() == null ? null : identities.get(e.subjectUserId());
            if (actor != null) b.names.add(actor.displayName());   // labels: resolved names only
        }

        List<FeedItemResponse> out = new ArrayList<>(buckets.size());
        for (Bucket b : buckets.values()) {
            List<String> lead = b.names.stream().limit(LEAD_LABELS).toList();
            out.add(FeedItemResponse.of(b.kind)
                    .group(b.groupId, ctx.groupName(b.groupId))
                    .collapsed(lead, b.count)
                    .ts(b.newest)
                    .cta(FeedCtaType.VIEW_BUBBLE, b.groupId)
                    .build());
        }
        return out;
    }

    /** Mutable per-(group, kind) accumulator: distinct actors + resolved names + newest time. */
    private static final class Bucket {
        final UUID groupId;
        final String kind;
        final Set<String> seen = new HashSet<>();
        final List<String> names = new ArrayList<>();   // resolved display names only
        int count;                                       // distinct actors incl. nameless
        Instant newest;
        Bucket(UUID groupId, String kind, Instant newest) {
            this.groupId = groupId; this.kind = kind; this.newest = newest;
        }
    }
}
