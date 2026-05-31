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

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * ACTIVITY: recent membership signals ("Maya joined Algorithms Bubble") derived
 * from SYSTEM_JOIN / SYSTEM_LEAVE chat messages. The actor's display name is
 * resolved in one batch lookup.
 */
@Component
@RequiredArgsConstructor
public class MembershipActivitySource implements FeedSource {

    private final ChatInternalService chatInternalService;
    private final AuthInternalService authInternalService;

    @Override
    public FeedSection section() {
        return FeedSection.ACTIVITY;
    }

    @Override
    public List<FeedItemResponse> fetch(FeedContext ctx) {
        List<MembershipEventItem> events =
                chatInternalService.findRecentMembershipEventsForGroups(ctx.groupIds(), ctx.fetchLimit());
        if (events.isEmpty()) return List.of();

        Set<UUID> actorIds = new HashSet<>();
        for (MembershipEventItem e : events) {
            if (e.subjectUserId() != null) actorIds.add(e.subjectUserId());
        }
        Map<UUID, UserIdentity> identities = actorIds.isEmpty()
                ? Map.of()
                : authInternalService.getIdentitiesByIds(actorIds);

        List<FeedItemResponse> out = new ArrayList<>(events.size());
        for (MembershipEventItem e : events) {
            UserIdentity actor = e.subjectUserId() == null ? null : identities.get(e.subjectUserId());
            String actorName = actor != null ? actor.displayName() : null;
            String kind = e.messageType() == ChatMessageType.SYSTEM_JOIN ? "memberJoin" : "memberLeave";
            out.add(FeedItemResponse.of(kind)
                    .group(e.groupId(), ctx.groupName(e.groupId()))
                    .subtitle(actorName)   // the "who"; frontend composes "<who> joined <bubble>"
                    .ts(e.sentAt())
                    .cta(FeedCtaType.VIEW_BUBBLE, e.groupId())
                    .build());
        }
        return out;
    }
}
