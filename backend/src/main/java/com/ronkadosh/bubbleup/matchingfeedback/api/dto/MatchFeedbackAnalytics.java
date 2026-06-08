package com.ronkadosh.bubbleup.matchingfeedback.api.dto;

import java.util.List;

/**
 * The reflection surface: aggregates that turn accumulated feedback into answers about
 * the matching engine.
 *
 * @param funnelByMode        shown→joined conversion for MATCHED vs TRENDING — does
 *                            complementarity matching convert better than popularity?
 * @param matchPercentBuckets join-rate + rating split per match-% band — does a higher
 *                            score predict joins and good-fit ratings?
 * @param ratings             overall explicit GOOD_FIT / BAD_FIT totals
 */
public record MatchFeedbackAnalytics(
        List<ModeFunnel> funnelByMode,
        List<PercentBucket> matchPercentBuckets,
        RatingTotals ratings
) {
    public record ModeFunnel(String mode, long shown, long joined, double joinRate) {}

    public record PercentBucket(String label, long shown, long joined, double joinRate,
                                long goodFit, long badFit) {}

    public record RatingTotals(long goodFit, long badFit) {}
}
