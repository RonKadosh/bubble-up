package com.ronkadosh.bubbleup.matching.application;

import com.ronkadosh.bubbleup.common.events.GroupMembershipChangedEvent;
import com.ronkadosh.bubbleup.common.events.UserBehaviorEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Drives matching recompute off domain events, asynchronously after commit.
 *
 * <p>Disabled when {@code app.matching.async-recompute=false} (the demo box): there
 * the recompute runs synchronously instead — {@code submitAnswer} applies it inline
 * and {@link com.ronkadosh.bubbleup.bootstrap.seed.DemoWorldSeeder} warms the world
 * once at seed time — so a single "Start demo" can't enqueue hundreds of tasks onto
 * the small pool and starve the guided tour's "matchable" gate.
 */
@Component
@ConditionalOnProperty(name = "app.matching.async-recompute", havingValue = "true", matchIfMissing = true)
@RequiredArgsConstructor
public class MatchingEventListener {

    private final MatchingCommandService commandService;

    @Async("matchingExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onUserBehavior(UserBehaviorEvent event) {
        commandService.applyUserBehavior(event.userId(), event.eventType());
    }

    @Async("matchingExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onMembershipChanged(GroupMembershipChangedEvent event) {
        commandService.recomputeGroupProfile(event.groupId());
        commandService.invalidateCacheForGroup(event.groupId());
    }

    @Async("matchingExecutor")
    @EventListener
    public void onBuildCacheRequest(BuildUserMatchCacheEvent event) {
        commandService.refreshUserMatchCache(event.userId());
    }
}
