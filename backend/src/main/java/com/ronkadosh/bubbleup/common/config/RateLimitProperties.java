package com.ronkadosh.bubbleup.common.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Master kill switch for {@code @RateLimit} enforcement. The actual limit/window
 * numbers live in the annotations on each endpoint — this only flips the whole
 * mechanism on or off (e.g. to disable in tests or as an emergency escape hatch).
 */
@ConfigurationProperties(prefix = "app.rate-limit")
public record RateLimitProperties(
        boolean enabled
) {}
