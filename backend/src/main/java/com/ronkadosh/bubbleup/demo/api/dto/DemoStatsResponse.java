package com.ronkadosh.bubbleup.demo.api.dto;

/**
 * Usage counters for the demo, returned by {@code GET /api/demo/stats}.
 *
 * @param totalStarts    all-time number of "Start demo" clicks (never-purged DemoStartLog rows)
 * @param activeSessions demo worlds currently alive (un-swept DemoSession rows)
 * @param last7Days      starts in the trailing 7 days
 */
public record DemoStatsResponse(
        long totalStarts,
        long activeSessions,
        long last7Days
) {}
