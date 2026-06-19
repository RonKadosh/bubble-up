package com.ronkadosh.bubbleup.common.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * Config for the public, no-login interactive demo (demo.bubbleup.online).
 *
 * <p>{@code enabled} is the master switch: every demo bean is gated on
 * {@code @ConditionalOnProperty(name = "app.demo.enabled", havingValue = "true")},
 * so with it off (the default, and forced off on the AWS prod deploy) none of the
 * demo code — the {@code /api/demo} endpoints, the per-session world seeder, the
 * cleanup sweep — even registers. Turned on only on the separate VPS demo deploy.
 *
 * <p>{@code sessionTtl} is the idle window after which an abandoned demo world is
 * swept; the client sends a heartbeat that bumps {@code DemoSession.lastSeenAt}.
 * {@code sweepInterval} is how often the cleanup job runs.
 */
@ConfigurationProperties(prefix = "app.demo")
public record DemoProperties(
        boolean enabled,
        Duration sessionTtl,
        Duration sweepInterval
) {
    public DemoProperties {
        if (sessionTtl == null) sessionTtl = Duration.ofHours(2);
        if (sweepInterval == null) sweepInterval = Duration.ofMinutes(10);
    }
}
