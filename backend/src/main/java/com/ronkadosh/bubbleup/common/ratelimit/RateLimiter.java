package com.ronkadosh.bubbleup.common.ratelimit;

import com.ronkadosh.bubbleup.common.datetime.TimeProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory fixed-window rate limiter. One {@link Window} per active key; the
 * window resets once {@code windowSeconds} elapse since it started. Clock comes
 * from the injected {@link TimeProvider} so tests can drive it deterministically.
 *
 * <p>Single-instance only — buckets are not shared across replicas. A multi-instance
 * deploy would need a Redis-backed store (see plan / Known gaps).
 */
@Component
@RequiredArgsConstructor
public class RateLimiter {

    private final TimeProvider timeProvider;

    private final ConcurrentHashMap<String, Window> windows = new ConcurrentHashMap<>();

    /** Mutable per-key window; all access is guarded by synchronizing on the instance. */
    private static final class Window {
        private Instant start;
        private int count;

        Window(Instant start) {
            this.start = start;
            this.count = 0;
        }
    }

    /**
     * Records one hit against {@code key} and reports whether it is within the limit.
     *
     * @return {@code true} if the request is allowed, {@code false} if the limit is exceeded.
     */
    public boolean tryAcquire(String key, int limit, int windowSeconds) {
        Instant now = timeProvider.now();
        Window window = windows.computeIfAbsent(key, k -> new Window(now));
        synchronized (window) {
            if (Duration.between(window.start, now).getSeconds() >= windowSeconds) {
                window.start = now;
                window.count = 0;
            }
            if (window.count >= limit) {
                return false;
            }
            window.count++;
            return true;
        }
    }

    /** Seconds until the current window for {@code key} resets, for a {@code Retry-After} header. */
    public long retryAfterSeconds(String key, int windowSeconds) {
        Window window = windows.get(key);
        if (window == null) {
            return windowSeconds;
        }
        synchronized (window) {
            long elapsed = Duration.between(window.start, timeProvider.now()).getSeconds();
            long remaining = windowSeconds - elapsed;
            return remaining > 0 ? remaining : 0;
        }
    }

    /**
     * Drops windows that have fully elapsed so the map doesn't grow without bound.
     * A window is evictable once it's older than a generous multiple of the longest
     * plausible window (1 hour for the expert-apply limit) — being conservative here
     * is cheap and avoids racing an in-flight window near its boundary.
     */
    @Scheduled(fixedDelay = 600_000L)
    void evictStaleWindows() {
        Instant cutoff = timeProvider.now().minus(Duration.ofHours(2));
        windows.values().removeIf(window -> {
            synchronized (window) {
                return window.start.isBefore(cutoff);
            }
        });
    }
}
