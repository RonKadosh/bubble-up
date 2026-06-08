package com.ronkadosh.bubbleup.matchingfeedback.application;

import com.ronkadosh.bubbleup.common.events.GroupJoinedEvent;
import com.ronkadosh.bubbleup.common.events.RecommendationsShownEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Persists the implicit feedback signals after the originating transaction commits,
 * off the request thread (reusing the matching executor). Explicit ratings come in
 * synchronously via {@code MatchFeedbackController} instead.
 */
@Component
@RequiredArgsConstructor
public class MatchFeedbackEventListener {

    private final MatchFeedbackService service;

    @Async("matchingExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onRecommendationsShown(RecommendationsShownEvent event) {
        service.recordImpressions(event.userId(), event.items());
    }

    @Async("matchingExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onGroupJoined(GroupJoinedEvent event) {
        service.recordJoin(event.userId(), event.groupId());
    }
}
