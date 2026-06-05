package com.ronkadosh.bubbleup.common.ratelimit;

/**
 * How a {@link RateLimit} bucket key is derived for a request. The key is always
 * additionally namespaced by the annotated controller method, so two endpoints
 * sharing a scope never collide.
 */
public enum RateLimitScope {

    /** One bucket per authenticated user. */
    PER_USER,

    /**
     * One bucket per client IP. Defined for future pre-auth surfaces (e.g. an OAuth
     * callback); no endpoint is annotated with it yet. Behind a reverse proxy this is
     * the raw socket IP — revisit {@code X-Forwarded-For} before relying on it.
     */
    PER_IP,

    /** One bucket per (user, room) — reads the {@code id}/{@code roomId} path variable. */
    PER_USER_PER_ROOM,

    /** One bucket per (user, group) — reads the {@code groupId} path variable. */
    PER_USER_PER_GROUP,

    /** One bucket per group across all users — reads the {@code groupId} path variable. */
    PER_GROUP,

    /** One bucket per file across all users — reads the {@code fileId} path variable. */
    PER_FILE
}
