package com.ronkadosh.bubbleup.demo.application;

import com.ronkadosh.bubbleup.auth.internal.AuthInternalService;
import com.ronkadosh.bubbleup.auth.internal.dto.AuthSession;
import com.ronkadosh.bubbleup.bootstrap.seed.DemoWorldHandle;
import com.ronkadosh.bubbleup.bootstrap.seed.DemoWorldSeeder;
import com.ronkadosh.bubbleup.common.datetime.TimeProvider;
import com.ronkadosh.bubbleup.demo.api.dto.DemoStartResponse;
import com.ronkadosh.bubbleup.demo.api.dto.DemoStatsResponse;
import com.ronkadosh.bubbleup.demo.model.DemoSession;
import com.ronkadosh.bubbleup.demo.model.DemoStartLog;
import com.ronkadosh.bubbleup.demo.persistence.DemoSessionRepository;
import com.ronkadosh.bubbleup.demo.persistence.DemoStartLogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.util.UUID;

/**
 * Orchestrates a "Start demo" click: build an isolated world, auto-log-in the
 * guest, and record the session for the idle-TTL sweep. Also handles the
 * heartbeat (bump {@code lastSeenAt}) and eager teardown.
 */
@Service
@ConditionalOnProperty(name = "app.demo.enabled", havingValue = "true")
@RequiredArgsConstructor
public class DemoSessionService {

    private final DemoWorldSeeder seeder;
    private final AuthInternalService authInternalService;
    private final DemoSessionRepository repository;
    private final DemoStartLogRepository startLogRepository;
    private final DemoWorldPurger purger;
    private final TimeProvider timeProvider;

    @Transactional
    public DemoStartResponse start() {
        String token = UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        DemoWorldHandle handle = seeder.seed(token);
        AuthSession session = authInternalService.issueSession(handle.guestUserId());

        repository.save(DemoSession.builder()
                .sessionToken(token)
                .universityId(handle.universityId())
                .guestUserId(handle.guestUserId())
                .createdAt(timeProvider.now())
                .lastSeenAt(timeProvider.now())
                .build());

        // Permanent tally — survives the idle sweep that purges the DemoSession above.
        startLogRepository.save(DemoStartLog.builder().startedAt(timeProvider.now()).build());

        return new DemoStartResponse(
                session.accessToken(),
                session.refreshToken(),
                new DemoStartResponse.DemoUser(
                        session.userId(), session.email(), session.role(),
                        session.displayName(), session.avatarUrl(), session.emailVerified()),
                handle.starterGroupId(),
                true);
    }

    @Transactional
    public void heartbeat(UUID guestUserId) {
        repository.findByGuestUserId(guestUserId)
                .ifPresent(ds -> ds.setLastSeenAt(timeProvider.now()));
    }

    /** Eager teardown when the visitor explicitly leaves; the sweep is the backstop. */
    public void end(UUID guestUserId) {
        repository.findByGuestUserId(guestUserId).ifPresent(purger::purgeWorld);
    }

    /** Usage counters for the operator's secret-key stats view. */
    @Transactional(readOnly = true)
    public DemoStatsResponse stats() {
        return new DemoStatsResponse(
                startLogRepository.count(),
                repository.count(),
                startLogRepository.countByStartedAtAfter(timeProvider.now().minus(Duration.ofDays(7))));
    }
}
