package com.ronkadosh.bubbleup.matching.internal.dto;

/**
 * The user's own private "profile strength" in the matching system — the part of
 * confidence they control (Daily Drops answered + meaningful activity). Surfaced
 * to the user so they can see their progression toward MATCHED recommendations.
 *
 * @param confidence        user_confidence in [0,1]
 * @param threshold         the matched-display threshold; at/above it, Matched picks can appear
 * @param matched           confidence ≥ threshold (the user has reached the unlock level)
 * @param answeredQuestions Daily Drops answered so far
 * @param questionCap       max questions that count toward confidence
 */
public record MatchingReliability(
        double confidence,
        double threshold,
        boolean matched,
        int answeredQuestions,
        int questionCap
) {}
