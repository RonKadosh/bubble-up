package com.ronkadosh.bubbleup.matching.application;

import com.ronkadosh.bubbleup.common.events.GroupMembershipChangedEvent;
import com.ronkadosh.bubbleup.common.events.UserBehaviorEvent;
import com.ronkadosh.bubbleup.common.events.BehaviorEventType;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
public class MatchingEventListener {

    private final MatchingCommandService commandService;

    @Async("matchingExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onUserBehavior(UserBehaviorEvent event) {
        if (event.eventType() == BehaviorEventType.USER_ANSWERED_QUIZ_QUESTION) {
            commandService.recomputeUserQuizProfile(event.userId());
        } else {
            commandService.recordBehaviorEvent(event.userId(), event.eventType());
        }
        commandService.recomputeGroupProfilesForUser(event.userId());
        commandService.refreshUserMatchCache(event.userId());
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
