package com.ronkadosh.bubbleup.common.ratelimit;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Repeatable;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Declares a rate limit on a controller handler method. Enforced by
 * {@code RateLimitInterceptor}: at most {@link #limit()} requests per
 * {@link #windowSeconds()} window, bucketed by {@link #scope()}.
 *
 * <p>Repeatable — a single endpoint may carry several limits (e.g. a per-group
 * <em>and</em> a per-user-per-group cap on file upload); the first exceeded wins.
 *
 * <p>This is declarative metadata, not logic — controllers stay thin.
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Repeatable(RateLimits.class)
public @interface RateLimit {

    /** Maximum number of requests permitted per window. */
    int limit();

    /** Window length in seconds. */
    int windowSeconds();

    /** How the bucket key is derived. */
    RateLimitScope scope();
}
