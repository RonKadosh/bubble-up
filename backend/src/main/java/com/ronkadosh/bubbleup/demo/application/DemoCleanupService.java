package com.ronkadosh.bubbleup.demo.application;

import com.ronkadosh.bubbleup.common.config.DemoProperties;
import com.ronkadosh.bubbleup.common.datetime.TimeProvider;
import com.ronkadosh.bubbleup.demo.model.DemoSession;
import com.ronkadosh.bubbleup.demo.persistence.DemoSessionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;

/**
 * Idle-TTL sweep: purges demo worlds whose guest hasn't sent a heartbeat within
 * {@code app.demo.session-ttl}. Runs every {@code app.demo.sweep-interval}. Each
 * world is purged in its own transaction (via {@link DemoWorldPurger}) so one bad
 * world doesn't abort the whole sweep.
 */
@Component
@ConditionalOnProperty(name = "app.demo.enabled", havingValue = "true")
@RequiredArgsConstructor
@Slf4j
public class DemoCleanupService {

    private final DemoSessionRepository repository;
    private final DemoWorldPurger purger;
    private final DemoProperties properties;
    private final TimeProvider timeProvider;

    @Scheduled(fixedDelayString = "${app.demo.sweep-interval}")
    public void sweepIdleWorlds() {
        Instant cutoff = timeProvider.now().minus(properties.sessionTtl());
        List<DemoSession> stale = repository.findByLastSeenAtBefore(cutoff);
        if (stale.isEmpty()) return;
        int purged = 0;
        for (DemoSession session : stale) {
            try {
                purger.purgeWorld(session);
                purged++;
            } catch (RuntimeException e) {
                log.warn("DemoCleanup: failed to purge world {} — will retry next sweep: {}",
                        session.getSessionToken(), e.getMessage());
            }
        }
        log.info("DemoCleanup: swept {}/{} idle demo worlds (idle > {})",
                purged, stale.size(), properties.sessionTtl());
    }
}
